"""One-shot remote sync via GitHub Git Data API (git smart-http is blocked here)."""
import os
import json
import base64
import subprocess
import tempfile

ROOT = os.path.dirname(os.path.abspath(__file__))
REPO = "LMD0924/AdmitAtlas"
TOKEN = os.environ["GH_TOKEN"]


def git(*args):
    return subprocess.run(["git", "-C", ROOT] + list(args), capture_output=True, check=True).stdout


def text(*args):
    return git(*args).decode("utf-8").strip()


def api(method, path, payload=None):
    tmp = None
    cmd = [
        "curl", "-sS", "--max-time", "60", "-X", method,
        "-H", "Authorization: Bearer " + TOKEN,
        "-H", "Accept: application/vnd.github+json",
        "-H", "X-GitHub-Api-Version: 2022-11-28",
        "-H", "Content-Type: application/json",
        "-H", "User-Agent: admitatlas-sync",
    ]
    if payload is not None:
        f = tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", suffix=".json", delete=False)
        json.dump(payload, f, ensure_ascii=False)
        f.close()
        tmp = f.name
        cmd += ["--data-binary", "@" + tmp]
    cmd.append("https://api.github.com/repos/" + REPO + "/" + path)
    r = subprocess.run(cmd, capture_output=True)
    if tmp:
        os.unlink(tmp)
    if r.returncode != 0:
        raise RuntimeError("curl failed: " + r.stderr.decode("utf-8", "replace"))
    return json.loads(r.stdout.decode("utf-8", "replace"))


local = text("rev-parse", "HEAD")
parent = text("rev-parse", "HEAD^")
local_tree = text("rev-parse", "HEAD^{tree}")
remote = api("GET", "git/ref/heads/main")["object"]["sha"]
print("local:", local, "| remote:", remote)
if remote != parent:
    raise SystemExit("remote is not local parent; refusing to sync")

changed = [p.decode("utf-8") for p in git("diff", "--name-only", "-z", parent, local).split(b"\0") if p]
print("changed files:", len(changed))
entries = []
for path in changed:
    full = os.path.join(ROOT, path)
    if not os.path.exists(full):
        entries.append({"path": path, "mode": "100644", "type": "blob", "sha": None})
        print("  delete", path)
        continue
    blob = api("POST", "git/blobs", {
        "content": base64.b64encode(open(full, "rb").read()).decode("ascii"),
        "encoding": "base64",
    })
    mode = text("ls-tree", local, "--", path).split()[0]
    entries.append({"path": path, "mode": mode, "type": "blob", "sha": blob["sha"]})
    print("  ", blob["sha"][:7], path)

base_tree = api("GET", "git/commits/" + remote)["tree"]["sha"]
tree = api("POST", "git/trees", {"base_tree": base_tree, "tree": entries})
print("tree matches local:", tree["sha"] == local_tree)
if tree["sha"] != local_tree:
    raise SystemExit("tree mismatch; remote ref NOT updated")

msg = git("cat-file", "commit", "HEAD").partition(b"\n\n")[2].decode("utf-8")
commit = api("POST", "git/commits", {
    "tree": tree["sha"],
    "parents": [remote],
    "message": msg,
    "author": {"name": text("log", "-1", "--format=%an"),
               "email": text("log", "-1", "--format=%ae"),
               "date": text("log", "-1", "--format=%aI")},
    "committer": {"name": text("log", "-1", "--format=%cn"),
                  "email": text("log", "-1", "--format=%ce"),
                  "date": text("log", "-1", "--format=%cI")},
})
sha = commit["sha"]
print("api commit:", sha, "| exact match:", sha == local)
api("PATCH", "git/refs/heads/main", {"sha": sha})
print("remote ref ->", sha)
if sha != local:
    text("update-ref", "refs/heads/main", sha, local)
    print("local ref retargeted ->", text("rev-parse", "HEAD"))
