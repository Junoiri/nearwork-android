package com.example.nearworkthesis.core.error

import org.junit.Assert.assertEquals
import org.junit.Test

class AppErrorTest {

    @Test
    fun appErrorVariants_preserveReasonStrings() {
        val import = AppError.ImportFailed("bad import")
        val export = AppError.ExportFailed("bad export")
        val database = AppError.DatabaseError("bad db")
        val parse = AppError.ParseFailed("bad parse")
        val unknown = AppError.Unknown("bad unknown")

        assertEquals("bad import", import.reason)
        assertEquals("bad export", export.reason)
        assertEquals("bad db", database.reason)
        assertEquals("bad parse", parse.reason)
        assertEquals("bad unknown", unknown.reason)
    }
}
