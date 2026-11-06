package com.admitatlas.score;

import com.admitatlas.common.ModuleProbe;
import org.springframework.stereotype.Service;

/**
 * Placeholder bean for the P0 skeleton. Real service implementation lands in P1.
 */
@Service
public class ScoreProbe implements ModuleProbe {

    @Override
    public String moduleName() {
        return "score";
    }
}
