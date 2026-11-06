package com.admitatlas.policyrule;

import com.admitatlas.common.ModuleProbe;
import org.springframework.stereotype.Service;

/**
 * Placeholder bean for the P0 skeleton. Real service implementation lands in P1.
 */
@Service
public class PolicyRuleProbe implements ModuleProbe {

    @Override
    public String moduleName() {
        return "policy-rule";
    }
}
