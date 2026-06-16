package com.example.nearworkthesis.feature

data class SampleImportOption(
    val label: String,
    val fileName: String
)

val SampleImportOptions: List<SampleImportOption> = listOf(
    SampleImportOption(label = "5 June 2026", fileName = "optodata_2026-06-05.csv"),
    SampleImportOption(label = "6 June 2026", fileName = "optodata_2026-06-06.csv"),
    SampleImportOption(label = "7 June 2026", fileName = "optodata_2026-06-07.csv"),
    SampleImportOption(label = "8 June 2026", fileName = "optodata_2026-06-08.csv")
)
