package com.darshan.ecommerce.common.exception;

import com.darshan.ecommerce.common.web.CorrelationIdFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import java.time.Clock;

// The single place that shapes an error body, used by the MVC exception handler and by the
// security entry points, so a 401 from the filter chain looks exactly like a 404 from a controller.
@Component
public class ProblemDetailFactory {

    private final Clock clock;

    public ProblemDetailFactory(Clock clock) {
        this.clock = clock;
    }

    public ProblemDetail create(HttpStatus status, String detail) {
        return stamp(ProblemDetail.forStatusAndDetail(status, detail));
    }

    public ProblemDetail stamp(ProblemDetail problem) {
        problem.setProperty("correlationId", CorrelationIdFilter.current());
        problem.setProperty("timestamp", clock.instant());
        return problem;
    }
}
