package com.example.nearworkthesis.core.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AppResultTest {

    @Test
    fun onSuccess_invokesBlock_onlyForSuccess_andReturnsSameInstance() {
        var successValue: Int? = null
        var failureCalls = 0
        val success: AppResult<Int> = AppResult.Success(7)

        val returned = success
            .onSuccess { successValue = it }
            .onFailure { failureCalls += 1 }

        assertEquals(7, successValue)
        assertEquals(0, failureCalls)
        assertSame(success, returned)
    }

    @Test
    fun onFailure_invokesBlock_onlyForFailure_andReturnsSameInstance() {
        var successCalls = 0
        var failureValue: AppError? = null
        val failure: AppResult<Int> = AppResult.Failure(AppError.Unknown("boom"))

        val returned = failure
            .onSuccess { successCalls += 1 }
            .onFailure { failureValue = it }

        assertEquals(0, successCalls)
        assertEquals(AppError.Unknown("boom"), failureValue)
        assertSame(failure, returned)
    }
}
