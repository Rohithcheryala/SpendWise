package com.example.spendwise.core.parser_pw.bank

class BankParserRegistry(private val parsers: List<BankParser>) {
    fun getParser(sender: String): BankParser? = parsers.firstOrNull { it.canHandle(sender) }
    fun all(): List<BankParser> = parsers
}


