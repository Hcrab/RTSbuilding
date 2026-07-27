package com.rtsbuilding.rtsbuilding.server.pipeline.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PipelineResultTest {

    @Test
    void successIsSingleton() {
        PipelineResult a = PipelineResult.success();
        PipelineResult b = PipelineResult.success();
        assertSame(a, b);
    }

    @Test
    void successIsSuccessRecord() {
        PipelineResult result = PipelineResult.success();
        assertInstanceOf(PipelineResult.Success.class, result);
    }

    @Test
    void failureCarriesMessage() {
        PipelineResult.Failure failure = new PipelineResult.Failure("test error");
        assertEquals("test error", failure.message());
    }

    @Test
    void factoryFailureCreatesFailure() {
        PipelineResult result = PipelineResult.failure("oops");
        assertInstanceOf(PipelineResult.Failure.class, result);
        assertEquals("oops", ((PipelineResult.Failure) result).message());
    }

    @Test
    void failureWithCause() {
        Throwable cause = new RuntimeException("root");
        PipelineResult.Failure failure = new PipelineResult.Failure("wrapped", cause);
        assertSame(cause, failure.cause());
    }

    @Test
    void failureWithoutCauseIsNull() {
        PipelineResult.Failure failure = new PipelineResult.Failure("no cause");
        assertNull(failure.cause());
    }

    @Test
    void skipCarriesReason() {
        PipelineResult.Skip skip = new PipelineResult.Skip("creative mode");
        assertEquals("creative mode", skip.reason());
    }

    @Test
    void factorySkipCreatesSkip() {
        PipelineResult result = PipelineResult.skip("bypassed");
        assertInstanceOf(PipelineResult.Skip.class, result);
        assertEquals("bypassed", ((PipelineResult.Skip) result).reason());
    }

    @Test
    void sealedExhaustiveSwitch() {
        PipelineResult result = PipelineResult.success();
        String label = switch (result) {
            case PipelineResult.Success s -> "ok";
            case PipelineResult.Failure f -> "fail";
            case PipelineResult.Skip s -> "skip";
        };
        assertEquals("ok", label);
    }

    @Test
    void failureAndSkipAreDistinctTypes() {
        PipelineResult f = PipelineResult.failure("err");
        PipelineResult s = PipelineResult.skip("skip");
        assertInstanceOf(PipelineResult.Failure.class, f);
        assertInstanceOf(PipelineResult.Skip.class, s);
    }

    @Test
    void multipleFailuresAreNotSingleton() {
        PipelineResult a = PipelineResult.failure("first");
        PipelineResult b = PipelineResult.failure("second");
        assertNotSame(a, b);
    }

    @Test
    void multipleSkipsAreNotSingleton() {
        PipelineResult a = PipelineResult.skip("a");
        PipelineResult b = PipelineResult.skip("b");
        assertNotSame(a, b);
    }
}
