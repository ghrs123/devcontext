package com.fitvision.domain.billing;

import com.fitvision.shared.exception.ErrorCode;
import com.fitvision.shared.exception.FitVisionException;

public class PlanLimitException extends FitVisionException {

    public PlanLimitException(String message) {
        super(ErrorCode.PLAN_LIMIT_REACHED, message);
    }
}
