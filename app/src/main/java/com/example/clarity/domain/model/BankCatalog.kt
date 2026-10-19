package com.example.clarity.domain.model

data class BankInstitution(val id: String, val name: String, val abbreviations: List<String>)

/** Offline catalog; IDs are independent of account IDs and never inferred from nicknames. */
object BankCatalog {
    val banks = listOf(
        BankInstitution("hnb", "Hatton National Bank", listOf("HNB")),
        BankInstitution("sampath", "Sampath Bank", listOf("Sampath")),
        BankInstitution("commercial", "Commercial Bank of Ceylon", listOf("ComBank", "Commercial")),
        BankInstitution("boc", "Bank of Ceylon", listOf("BOC")),
        BankInstitution("peoples", "People's Bank", listOf("Peoples", "People’s")),
        BankInstitution("standard_chartered", "Standard Chartered", listOf("SC", "SCB")),
        BankInstitution("hsbc", "HSBC", listOf("Hongkong and Shanghai Banking Corporation", "Hong Kong")),
        BankInstitution("seylan", "Seylan Bank", listOf("Seylan")),
        BankInstitution("nsb", "National Savings Bank", listOf("NSB")),
        BankInstitution("ntb", "Nations Trust Bank", listOf("NTB")),
        BankInstitution("ndb", "National Development Bank", listOf("NDB")),
        BankInstitution("amana", "Amana Bank", listOf("Amãna")),
        BankInstitution("cargills", "Cargills Bank", listOf("Cargills")),
        BankInstitution("dfcc", "DFCC Bank", listOf("DFCC")),
        BankInstitution("pan_asia", "Pan Asia Banking Corporation", listOf("PABC", "Pan Asia")),
        BankInstitution("union", "Union Bank of Colombo", listOf("Union Bank", "UBC")),
        BankInstitution("other", "Other Bank / Financial Institution", listOf("Other"))
    )
    fun find(id: String) = banks.firstOrNull { it.id == id }
    fun search(query: String): List<BankInstitution> {
        val words = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        return banks.filter { bank -> words.all { word ->
            (listOf(bank.name) + bank.abbreviations).any { it.contains(word, ignoreCase = true) }
        } }
    }
}
