import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.datetime.internal.JSJoda.LocalDate
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import org.w3c.dom.get


val defaultDecimalMode = DecimalMode(decimalPrecision = 10, roundingMode = RoundingMode.ROUND_HALF_TO_EVEN)
infix fun BigDecimal.div(other: BigDecimal) = this.divide(other, defaultDecimalMode)
val String.bdc get() = this.toBigDecimal(decimalMode = defaultDecimalMode)
val Int.bdc get() = this.toBigDecimal(decimalMode = defaultDecimalMode)
val zero: BigDecimal = BigDecimal.ZERO
fun max(a: BigDecimal, b: BigDecimal) = if (a > b) a else b
fun min(a: BigDecimal, b: BigDecimal) = if (a > b) b else a
fun positive(a: BigDecimal) = max(a, zero)
fun BigDecimal.str(deduction: Boolean = true): String {
    val value = this.roundToDigitPositionAfterDecimalPoint(2, RoundingMode.ROUND_HALF_TO_EVEN).toPlainString()
    return if (deduction) value else """<span class="has-text-primary-dark">$value</span>"""
}
fun BigDecimal.sc(decimalDigits: Int = 2): BigDecimal = this.roundToDigitPositionAfterDecimalPoint(decimalDigits.toLong(), RoundingMode.ROUND_HALF_TO_EVEN)
fun BigDecimal.percentString() = (this * 100.bdc).roundToDigitPositionAfterDecimalPoint(2, RoundingMode.ROUND_HALF_TO_EVEN).toPlainString() + "%"
fun BigDecimal.minusPenny(): BigDecimal = this - "0.01".bdc.sc()
fun Iterable<BigDecimal>.sum() = reduceOrNull(BigDecimal::add) ?: zero
fun <T> Iterable<T>.sumOf(selector: (T) -> BigDecimal) = map(selector).reduceOrNull(BigDecimal::add) ?: zero
fun <T> Iterable<T>.avgOf(selector: (T) -> BigDecimal) = (sumOf(selector) div this.count().bdc).sc(2)

val colBorderIndexes = listOf(4, 8, 11, 18, 25, 27, 32)
val sectionClassToIndex = mapOf(
    0..3 to "sect-calendar",
    4..7 to "sect-income",
    8..10 to "sect-food",
    11..17 to "sect-zus",
    18..24 to "sect-pit",
    25..26 to "sect-deductions",
    27..31 to "sect-nett",
)
val extraColIndexes = listOf(2, 3, 4, 5, 7, 11, 12, 18, 19, 22, 23, 29, 30)
val extraColBorderHolderIndexes = listOf(6, 13, 20)
const val deleteItemSpan = "<button class=\"delete\" onclick=\"this.parentElement.remove();\"></button>"
fun isHidden(sect: String) = if ((document.getElementById("show-$sect") as? HTMLInputElement)?.checked == true) "" else "is-hidden"
fun cColSpan(sect: String): Int {
    val range = sectionClassToIndex.filterValues { it == sect }.keys.first()
    val element = document.getElementById("expand-$sect") as? HTMLInputElement
    return if (element == null || element.checked) range.count() else range.count { it !in extraColIndexes }
}
fun cClass(i: Int): String {
    val sectBorder = if (i in colBorderIndexes) "section-border" else ""
    val section = sectionClassToIndex.firstNotNullOf { if (i in it.key) it.value else null }
    val hidden = isHidden(section)
    val extraColClass = if (i in extraColIndexes) "extra-sect-column" else ""
    val extraColBorderHoldersClass = if (i in extraColBorderHolderIndexes) "extra-sect-border-holder" else ""
    val expandCheckbox = document.getElementById("expand-$section") as? HTMLInputElement
    val collapsed = if (expandCheckbox == null || expandCheckbox.checked) "" else "is-collapsed"
    return "$section $sectBorder $hidden $extraColClass $extraColBorderHoldersClass $collapsed"
}
fun hTooltip(i: Int) = "has-tooltip-${when (i) { 0 -> "right"; 31 -> "left" else -> "bottom"}}"


fun main() {
    BigDecimal.useToStringExpanded = true

    addListHandlers()
    addSectionShowHandlers()

    document.getElementById("input")?.addEventListener("submit", { event ->
        event.preventDefault()
        try {
            submitTheCalculation()
        } catch (e: dynamic) {
            val element = document.getElementById("output")
            element?.innerHTML = "<div class=\"notification is-danger\">Input failure: $e</div>"
        }
    })

    document.addEventListener("DOMContentLoaded", {
        val cardToggles = document.getElementsByClassName("card-toggle").asList()
        for ( cardToggle in cardToggles) {
            cardToggle.addEventListener("click", { e ->
                val cardElement = (e.currentTarget as? HTMLElement)?.parentElement
                (cardElement?.childNodes?.get(3) as? HTMLElement)?.classList?.toggle("is-hidden")
            })
        }
    })

    addBackupClickListener()

    val backupString: String? = document.location?.search
        ?.ifBlank { null }
        ?.substring(1)
        ?.split("&")
        ?.map { it.split("=") }
        ?.firstOrNull {
            it.size == 2 && it[0] == "backup"
        }?.get(1)
    if (backupString == null) {
        submitTheCalculation()
    } else {
        (document.getElementById("backup") as HTMLInputElement).value = backupString
        loadAndRunBackup()
    }
}

private fun addBackupClickListener() {
    document.getElementById("backup-form")?.addEventListener("submit", { event ->
        event.preventDefault()
        loadAndRunBackup()
    })
    val backupCopy = document.getElementById("backup-copy")
    if (backupCopy != null) {
        backupCopy.addEventListener("click", { event ->
            event.preventDefault()
            val copyText = (document.getElementById("backup") as HTMLInputElement)
            copyText.select()
            copyText.setSelectionRange(0, 99999)
            window.navigator.clipboard.writeText(copyText.value)
            backupCopy.innerHTML = "<i class=\"fas fa-check\"></i>"
        })
        backupCopy.addEventListener("mouseout", {
            backupCopy.innerHTML = "<i class=\"fas fa-copy\"></i>"
        })
    }
}

private fun loadAndRunBackup() {
    try {
        val backupBase64 = (document.getElementById("backup") as HTMLInputElement).value
        val backup = window.atob(backupBase64)
        val fields = backup.split("|")

        (document.getElementById("salaryMonthlyGross") as HTMLInputElement).value = fields[0]
        (document.getElementById("startDate") as HTMLInputElement).value = fields[1]
        (document.getElementById("adultDate") as HTMLInputElement).value = fields[2]
        (document.getElementById("sharedTaxDeclaration") as HTMLInputElement).checked = fields[3].toBooleanStrict()
        (document.getElementById("foodVouchersFullyCompensated") as HTMLInputElement).checked = fields[4].toBooleanStrict()
        (document.getElementById("dailyFoodVouchersIncome") as HTMLInputElement).value = fields[5]
        (document.getElementById("permanentMonthlyNettDeductions") as HTMLInputElement).value = fields[6]
        (document.getElementById("participateInPPK") as HTMLInputElement).checked = fields[7].toBooleanStrict()
        (document.getElementById("liveOutsideOfCity") as HTMLInputElement).checked = fields[8].toBooleanStrict()
        (document.getElementById("withTargetBonus") as HTMLInputElement).checked = fields[9].toBooleanStrict()
        (document.getElementById("annualTargetBonusRatio") as HTMLInputElement).value = fields[10]
        (document.getElementById("quarterTargetBonusRatio") as HTMLInputElement).value = fields[11]
        (document.getElementById("useNewRulesAfter2022") as HTMLInputElement).checked = fields[12].toBooleanStrict()
        (document.getElementById("usdRate") as HTMLInputElement).value = fields[13]
        setFromMap("salaryChange", fields[14])
        setFromMap("actualAnnualBonus", fields[15])
        setFromMap("actualQuarterBonus", fields[16])
        setFromMap("occasionalBonusesGross", fields[17])
        setFromMap("occasionalNettDeductions", fields[18])
        setFromMap("actualFoodVouchers", fields[19])
        setFromMap("sickLeaves", fields[20])
        // added after first release, need defaults for backup backward compatibility
        (document.getElementById("creativeWorkStart") as HTMLInputElement).value = fields.getOrNull(21) ?: "2022-01"
        (document.getElementById("creativeWorkPercent") as HTMLInputElement).value = fields.getOrNull(22) ?: "0"
        (document.getElementById("endDate") as HTMLInputElement).value = fields.getOrNull(23) ?: "2030-01-01"
        (document.getElementById("useNewRulesAfterJuly2022") as HTMLInputElement).checked = fields.getOrNull(24)?.toBooleanStrict() ?: true
        submitTheCalculation()
    } catch (e: dynamic) {
        val element = document.getElementById("output")
        element?.innerHTML = "Backup failure: $e"
    }
}

private fun submitTheCalculation() {
    val backup = mutableListOf<String>()

    val salaryMonthlyGross = (document.getElementById("salaryMonthlyGross") as HTMLInputElement).value.bdc
    backup.add((document.getElementById("salaryMonthlyGross") as HTMLInputElement).value)
    val startDate = LocalDate.parse((document.getElementById("startDate") as HTMLInputElement).value)
    backup.add((document.getElementById("startDate") as HTMLInputElement).value)
    val adultDate = LocalDate.parse((document.getElementById("adultDate") as HTMLInputElement).value)
    backup.add((document.getElementById("adultDate") as HTMLInputElement).value)
    val sharedTaxDeclaration = (document.getElementById("sharedTaxDeclaration") as HTMLInputElement).checked
    backup.add((document.getElementById("sharedTaxDeclaration") as HTMLInputElement).checked.toString())
    val foodVouchersFullyCompensated = (document.getElementById("foodVouchersFullyCompensated") as HTMLInputElement).checked
    backup.add((document.getElementById("foodVouchersFullyCompensated") as HTMLInputElement).checked.toString())
    val dailyFoodVouchersIncome = (document.getElementById("dailyFoodVouchersIncome") as HTMLInputElement).value.bdc
    backup.add((document.getElementById("dailyFoodVouchersIncome") as HTMLInputElement).value)
    val permanentMonthlyNettDeductions = (document.getElementById("permanentMonthlyNettDeductions") as HTMLInputElement).value.bdc
    backup.add((document.getElementById("permanentMonthlyNettDeductions") as HTMLInputElement).value)
    val participateInPPK = (document.getElementById("participateInPPK") as HTMLInputElement).checked
    backup.add((document.getElementById("participateInPPK") as HTMLInputElement).checked.toString())
    val liveOutsideOfCity = (document.getElementById("liveOutsideOfCity") as HTMLInputElement).checked
    backup.add((document.getElementById("liveOutsideOfCity") as HTMLInputElement).checked.toString())
    val withTargetBonus = (document.getElementById("withTargetBonus") as HTMLInputElement).checked
    backup.add((document.getElementById("withTargetBonus") as HTMLInputElement).checked.toString())
    val annualTargetBonusRatio = (document.getElementById("annualTargetBonusRatio") as HTMLInputElement).value.bdc
    backup.add((document.getElementById("annualTargetBonusRatio") as HTMLInputElement).value)
    val quarterTargetBonusRatio = (document.getElementById("quarterTargetBonusRatio") as HTMLInputElement).value.bdc
    backup.add((document.getElementById("quarterTargetBonusRatio") as HTMLInputElement).value)
    val useNewRulesAfter2022 = (document.getElementById("useNewRulesAfter2022") as HTMLInputElement).checked
    backup.add((document.getElementById("useNewRulesAfter2022") as HTMLInputElement).checked.toString())
    val usdRate = (document.getElementById("usdRate") as HTMLInputElement).value.bdc
    backup.add((document.getElementById("usdRate") as HTMLInputElement).value)
    val (contractGrossSalaryChange, contractGrossSalaryChangeBackup) = loadToMap("salaryChange")
    backup.add(contractGrossSalaryChangeBackup.joinToString(","))
    val (actualAnnualBonusByMonth, actualAnnualBonusByMonthBackup) = loadToMap("actualAnnualBonus")
    backup.add(actualAnnualBonusByMonthBackup.joinToString(","))
    val (actualQuarterBonusByMonth, actualQuarterBonusByMonthBackup) = loadToMap("actualQuarterBonus")
    backup.add(actualQuarterBonusByMonthBackup.joinToString(","))
    val (occasionalBonusesGross, occasionalBonusesGrossBackup) = loadToMap("occasionalBonusesGross")
    backup.add(occasionalBonusesGrossBackup.joinToString(","))
    val (occasionalNettDeductionsByMonth, occasionalNettDeductionsByMonthBackup) = loadToMap("occasionalNettDeductions")
    backup.add(occasionalNettDeductionsByMonthBackup.joinToString(","))
    val (actualFoodVouchersByMonth, actualFoodVouchersByMonthBackup) = loadToMap("actualFoodVouchers")
    backup.add(actualFoodVouchersByMonthBackup.joinToString(","))
    val sickLeavesBackup = mutableListOf<String>()
    val sickLeaves = (document.getElementById("sickLeaves"))?.children?.asList()?.map {
        val value = it.innerHTML.removePrefix(deleteItemSpan).trim()
        val items = value.split(" -&gt; ")
        sickLeavesBackup.add(value)
        LocalDate.parse(items[0]) to LocalDate.parse(items[1])
    }.orEmpty()
    backup.add(sickLeavesBackup.joinToString(","))

    // added after first release
    val creativeWorkStart = Month((document.getElementById("creativeWorkStart") as HTMLInputElement).value)
    backup.add((document.getElementById("creativeWorkStart") as HTMLInputElement).value)
    val creativeWorkPercent = (document.getElementById("creativeWorkPercent") as HTMLInputElement).value.bdc
    backup.add((document.getElementById("creativeWorkPercent") as HTMLInputElement).value)
    val endDate = LocalDate.parse((document.getElementById("endDate") as HTMLInputElement).value)
    backup.add((document.getElementById("endDate") as HTMLInputElement).value)
    val useNewRulesAfterJuly2022 = (document.getElementById("useNewRulesAfterJuly2022") as HTMLInputElement).checked
    backup.add((document.getElementById("useNewRulesAfterJuly2022") as HTMLInputElement).checked.toString())
    // creating backup
    (document.getElementById("backup") as HTMLInputElement).value = window.btoa(backup.joinToString("|"))
    val input = Input(
        salaryMonthlyGross = salaryMonthlyGross,
        startDate = startDate,
        birthday26years = adultDate,
        sharedTaxDeclaration = sharedTaxDeclaration,
        foodVouchersFullyCompensated = foodVouchersFullyCompensated,
        dailyFoodVouchersIncome = dailyFoodVouchersIncome,
        permanentMonthlyNettDeductions = permanentMonthlyNettDeductions,
        participateInPPK = participateInPPK,
        liveOutsideOfCity = liveOutsideOfCity,
        useNewRulesAfter2022 = useNewRulesAfter2022,
        useNewRulesAfterJuly2022 = useNewRulesAfterJuly2022,
        withTargetBonus = withTargetBonus,
        usdRate = usdRate,
        contractGrossSalaryChange = contractGrossSalaryChange,
        actualAnnualBonusByMonth = actualAnnualBonusByMonth,
        actualQuarterBonusByMonth = actualQuarterBonusByMonth,
        occasionalBonusesGross = occasionalBonusesGross,
        occasionalNettDeductionsByMonth = occasionalNettDeductionsByMonth,
        actualFoodVouchersByMonth = actualFoodVouchersByMonth,
        sickLeaves = sickLeaves,
        quarterTargetBonusRatio = quarterTargetBonusRatio,
        annualTargetBonusRatio = annualTargetBonusRatio,
        creativeWorkStart = creativeWorkStart,
        creativeWorkPercent = creativeWorkPercent,
        endDate = endDate
    )
    updateTheCalculation(input)
}

private fun setFromMap(id: String, valueList: String) {
    val values = valueList.split(",")
    val element = document.getElementById(id)
    if (element != null) {
        element.innerHTML = ""
        if (valueList.isNotBlank()) {
            for (value in values) {
                val node = document.createElement("li")
                node.innerHTML = "$deleteItemSpan $value"
                element.appendChild(node)
            }
        }
    }
}

private fun loadToMap(id: String, backup: MutableList<String> = mutableListOf()) =
    (document.getElementById(id))?.children?.asList()?.associate {
        val value = it.innerHTML.removePrefix(deleteItemSpan).trim()
        val items = value.split(" -&gt; ")
        backup.add(value)
        Month(items[0]) to items[1].bdc
    }.orEmpty() to backup

private fun addSectionShowHandlers() {
    document.getElementById("show-sect-calendar")?.addEventListener("change", {
        document.getElementsByClassName("sect-calendar").asList().forEach { it.classList.toggle("is-hidden") }
    })
    document.getElementById("show-sect-income")?.addEventListener("change", {
        document.getElementsByClassName("sect-income").asList().forEach { it.classList.toggle("is-hidden") }
    })
    document.getElementById("show-sect-food")?.addEventListener("change", {
        document.getElementsByClassName("sect-food").asList().forEach { it.classList.toggle("is-hidden") }
    })
    document.getElementById("show-sect-zus")?.addEventListener("change", {
        document.getElementsByClassName("sect-zus").asList().forEach { it.classList.toggle("is-hidden") }
    })
    document.getElementById("show-sect-pit")?.addEventListener("change", {
        document.getElementsByClassName("sect-pit").asList().forEach { it.classList.toggle("is-hidden") }
    })
    document.getElementById("show-sect-nett")?.addEventListener("change", {
        document.getElementsByClassName("sect-nett").asList().forEach { it.classList.toggle("is-hidden") }
    })
    document.getElementById("show-sect-deductions")?.addEventListener("change", {
        document.getElementsByClassName("sect-deductions").asList().forEach { it.classList.toggle("is-hidden") }
    })


    document.getElementById("expand-sect-calendar")?.addEventListener("change", {
        document.getElementsByClassName("sect-calendar extra-sect-column").asList().forEach { it.classList.toggle("is-collapsed") }
        document.getElementsByClassName("sect-calendar extra-sect-border-holder").asList().forEach { it.classList.toggle("is-collapsed") }
        document.getElementById("header-sect-calendar")?.setAttribute("colspan", cColSpan("sect-calendar").toString())
    })
    document.getElementById("expand-sect-income")?.addEventListener("change", {
        document.getElementsByClassName("sect-income extra-sect-column").asList().forEach { it.classList.toggle("is-collapsed") }
        document.getElementsByClassName("sect-income extra-sect-border-holder").asList().forEach { it.classList.toggle("is-collapsed") }
        document.getElementById("header-sect-income")?.setAttribute("colspan", cColSpan("sect-income").toString())
    })
    document.getElementById("expand-sect-zus")?.addEventListener("change", {
        document.getElementsByClassName("sect-zus extra-sect-column").asList().forEach { it.classList.toggle("is-collapsed") }
        document.getElementsByClassName("sect-zus extra-sect-border-holder").asList().forEach { it.classList.toggle("is-collapsed") }
        document.getElementById("header-sect-zus")?.setAttribute("colspan", cColSpan("sect-zus").toString())
    })
    document.getElementById("expand-sect-pit")?.addEventListener("change", {
        document.getElementsByClassName("sect-pit extra-sect-column").asList().forEach { it.classList.toggle("is-collapsed") }
        document.getElementsByClassName("sect-pit extra-sect-border-holder").asList().forEach { it.classList.toggle("is-collapsed") }
        document.getElementById("header-sect-pit")?.setAttribute("colspan", cColSpan("sect-pit").toString())
    })
    document.getElementById("expand-sect-nett")?.addEventListener("change", {
        document.getElementsByClassName("sect-nett extra-sect-column").asList().forEach { it.classList.toggle("is-collapsed") }
        document.getElementsByClassName("sect-nett extra-sect-border-holder").asList().forEach { it.classList.toggle("is-collapsed") }
        document.getElementById("header-sect-nett")?.setAttribute("colspan", cColSpan("sect-nett").toString())
    })
}

private fun addListHandlers() {
    document.getElementById("salaryChangeUpdate")?.addEventListener("click", { event ->
        event.preventDefault()
        val salaryChangeMonth = (document.getElementById("salaryChangeMonth") as HTMLInputElement).value
        val salaryChangeValue = (document.getElementById("salaryChangeValue") as HTMLInputElement).value.bdc
        val node = document.createElement("li")
        node.innerHTML = "$deleteItemSpan $salaryChangeMonth -&gt; ${salaryChangeValue.str()}"
        document.getElementById("salaryChange")?.appendChild(node)
    })

    document.getElementById("sickLeavesUpdate")?.addEventListener("click", { event ->
        event.preventDefault()
        val sickLeavesStart = (document.getElementById("sickLeavesStart") as HTMLInputElement).value
        val sickLeavesEnd = (document.getElementById("sickLeavesEnd") as HTMLInputElement).value
        val node = document.createElement("li")
        node.innerHTML = "$deleteItemSpan $sickLeavesStart -&gt; $sickLeavesEnd"
        document.getElementById("sickLeaves")?.appendChild(node)
    })

    document.getElementById("actualQuarterBonusUpdate")?.addEventListener("click", { event ->
        event.preventDefault()
        val actualQuarterBonusMonth = (document.getElementById("actualQuarterBonusMonth") as HTMLInputElement).value
        val actualQuarterBonusValue = (document.getElementById("actualQuarterBonusValue") as HTMLInputElement).value.bdc
        val node = document.createElement("li")
        node.innerHTML = "$deleteItemSpan $actualQuarterBonusMonth -&gt; ${actualQuarterBonusValue.str()}"
        document.getElementById("actualQuarterBonus")?.appendChild(node)
    })

    document.getElementById("actualAnnualBonusUpdate")?.addEventListener("click", { event ->
        event.preventDefault()
        val actualAnnualBonusMonth = (document.getElementById("actualAnnualBonusMonth") as HTMLInputElement).value
        val actualAnnualBonusValue = (document.getElementById("actualAnnualBonusValue") as HTMLInputElement).value.bdc
        val node = document.createElement("li")
        node.innerHTML = "$deleteItemSpan $actualAnnualBonusMonth -&gt; ${actualAnnualBonusValue.str()}"
        document.getElementById("actualAnnualBonus")?.appendChild(node)
    })

    document.getElementById("occasionalBonusesGrossUpdate")?.addEventListener("click", { event ->
        event.preventDefault()
        val occasionalBonusesGrossMonth =
            (document.getElementById("occasionalBonusesGrossMonth") as HTMLInputElement).value
        val occasionalBonusesGrossValue =
            (document.getElementById("occasionalBonusesGrossValue") as HTMLInputElement).value.bdc
        val node = document.createElement("li")
        node.innerHTML = "$deleteItemSpan $occasionalBonusesGrossMonth -&gt; ${occasionalBonusesGrossValue.str()}"
        document.getElementById("occasionalBonusesGross")?.appendChild(node)
    })

    document.getElementById("occasionalNettDeductionsUpdate")?.addEventListener("click", { event ->
        event.preventDefault()
        val occasionalNettDeductionsMonth =
            (document.getElementById("occasionalNettDeductionsMonth") as HTMLInputElement).value
        val occasionalNettDeductionsValue =
            (document.getElementById("occasionalNettDeductionsValue") as HTMLInputElement).value.bdc
        val node = document.createElement("li")
        node.innerHTML = "$deleteItemSpan $occasionalNettDeductionsMonth -&gt; ${occasionalNettDeductionsValue.str()}"
        document.getElementById("occasionalNettDeductions")?.appendChild(node)
    })

    document.getElementById("actualFoodVouchersUpdate")?.addEventListener("click", { event ->
        event.preventDefault()
        val actualFoodVouchersMonth = (document.getElementById("actualFoodVouchersMonth") as HTMLInputElement).value
        val actualFoodVouchersValue = (document.getElementById("actualFoodVouchersValue") as HTMLInputElement).value.bdc
        val node = document.createElement("li")
        node.innerHTML = "$deleteItemSpan $actualFoodVouchersMonth -&gt; ${actualFoodVouchersValue.str()}"
        document.getElementById("actualFoodVouchers")?.appendChild(node)
    })
}

private fun updateTheCalculation(input: Input) {
    val topHeader = listOf(
        """<th id="header-sect-calendar" colspan="${cColSpan("sect-calendar")}" class="               sect-calendar ${isHidden("sect-calendar")} has-text-white" style="border-bottom:0;">Calendar</th>""",
        """<th id="header-sect-income" colspan="${cColSpan("sect-income")}" class="section-border sect-income ${isHidden("sect-income")} has-text-white" style="border-bottom:0;">Income</th>""",
        """<th id="header-sect-food" colspan="${cColSpan("sect-food")}" class="section-border sect-food ${isHidden("sect-food")} has-text-white" style="border-bottom:0;">Food Vouchers</th>""",
        """<th id="header-sect-zus" colspan="${cColSpan("sect-zus")}" class="section-border sect-zus ${isHidden("sect-zus")} has-text-white" style="border-bottom:0;">ZUS (Social Insurance</th>""",
        """<th id="header-sect-pit" colspan="${cColSpan("sect-pit")}" class="section-border sect-pit ${isHidden("sect-pit")} has-text-white" style="border-bottom:0;">PIT (Income Tax</th>""",
        """<th id="header-sect-deductions" colspan="${cColSpan("sect-deductions")}" class="section-border sect-deductions ${isHidden("sect-deductions")} has-text-white" style="border-bottom:0;">Deductions</th>""",
        """<th id="header-sect-nett" colspan="${cColSpan("sect-nett")}" class="section-border sect-nett ${isHidden("sect-nett")} has-text-white" style="border-bottom:0;">Money on hand</th>""",
    ).joinToString("\n", prefix = "<tr>", postfix = "</tr>")

    val header = listOf(
        "Year" to "A Year"
        , "Month" to "Note that this is a calculation month, not the one when the salary is paid"
        , "Work days" to "Amount of working days in this month (sick days are included in this value)"
        , "Sick days" to "Amount of sickness days in this month"
        , "Contract" to "Configured gross salary, goes from settings"
        , "Bonuses" to "Amount of bonuses received this month, this includes quarter/annual bonuses and occasional compensations"
        , "Total Gross" to "Total gross, received this month, with respect of working and sick days as well as with Food Vouchers tax compensation (if any)"
        , "Gross USD" to "Total Gross in USD, received this month, including Food Vouchers tax compensation (if any)"
        , "Total" to "Total amount money of received in food vouchers"
        , "Under ZUS" to "Amount of money in food vouchers which are subject of tax (190zł per month is tax free). Only ZUS is paid from this amount"
        , "Tax Compensation" to "In case company fully compensates food vouchers, a bonus will be added which will compensate the ZUS taxes from food vouchers and also compensate itself"
        , "Yearly Base" to "Yearly Base for ZUS tax, retirement and disability taxes have yearly limit which is calculated from this one"
        , "Month Base" to "Month Base from which ZUS tax will be calculated, this is 'Total Gross' + 'Food Vouchers'"
        , "Retirement" to "Retirement Tax (Emerytalne), 9.76% with limit of 157770zł in 2021 and 177660zł in 2022 (kwota 30-krotności)"
        , "Disability" to "Disability Tax (Rentowe), 1.5% with limit of 157770zł in 2021 and 177660zł in 2022 (kwota 30-krotności)"
        , "Sickness" to "Sickness Tax (Chorobowe), always 2.45% for worker with no limit"
        , "Health" to "Health Tax (Zdrowotne), 9%, calculates differently based on month and inputs, please refer the logs for more info"
        , "ZUS Total" to "Total amount payed to ZUS"
        , "Yearly Base" to "Yearly Base for PIT tax, based on this either 17% or 32% tax is applied."
        , "Month Base" to "Monthly Base for PIT tax, this is a 'Total Gross' minus ZUS and some other minor reduces"
        , "17% Tax" to "Amount of money paid with 17% tax in this month"
        , "32% Tax" to "Amount of money paid with 32% tax in this month"
        , "Tax Free" to "A tax free amount which will be deducted from PIT, this value is based on minimal TAX-free salary"
        , "Health Reduce" to "Until 2022, a person had a right to deduct from PIT a 7.75% of 9% paid to Health Insurance "
        , "PIT Total" to "Total amount of PIT paid this month with all tax free and deductions"
        , "PPK" to "Amount of money paid to PPK (2% of gross income)"
        , "Other" to "Other occasional or permanent deductions listed in settings"
        , "Net Income" to "Money paid 'in hands' after all deductions and taxes"
        , "Rolling AVG" to "Rolling average from year start (each new month in a year correct the average)"
        , "Net USD" to "Money paid 'in hands' IN USD after all deductions and taxes"
        , "AVG USD" to "Rolling average IN USD from year start (each new month in a year correct the average)"
        , "Tax %" to "Total amount of taxes and deductions paid this month, basically 'Gross' minus 'Nett' divide 'Gross'"
    )
        .mapIndexed { i, it -> "<th class=\"has-text-white is-underlined has-tooltip-multiline ${hTooltip(i)} ${cClass(i)} year-border\" data-tooltip=\"${it.second.replace('"', '\'')}\">${it.first}</th>" }
        .joinToString("\n", prefix = "<thead class=\"has-text-centered has-text-weight-bold has-background-info\">\n$topHeader\n<tr>", postfix = "</tr>\n</thead>")

    val taxMonths = UopCalcualtor(input).calculate()
    val tableRows = taxMonths.groupBy { it.month.year }.asSequence().joinToString("\n") { (year, months) ->
        val monthRows = months.joinToString("\n") {
            with(it) {
                listOf(
                    "$year",
                    month.name,
                    "$workdays",
                    "$sickDays",
                    contractIncome.str(false),
                    bonusesIncome.str(false),
                    grossIncome.str(false),
                    (grossIncome div input.usdRate).str(false),
                    foodVouchersTotal.str(false),
                    foodVouchersTaxable.str(),
                    foodVouchersCompensation.str(false),
                    yearlyZusBase.str(),
                    zusBase.str(),
                    retirementTax.str(),
                    disabilityTax.str(),
                    sicknessTax.str(),
                    healthTax.str(),
                    zus.str(),
                    yearlyPitBase.str(),
                    pitBase.str(),
                    normalPIT.str(),
                    increasedPIT.str(),
                    pitTaxFree.str(),
                    pitHealthReduce.str(),
                    pit.str(),
                    ppkTax.str(),
                    nettDeductions.str(),
                    nettIncome.str(false),
                    months.take(months.count { m -> m.month.year == month.year && m.month <= month })
                        .avgOf { m -> m.nettIncome }.str(false),
                    (nettIncome div input.usdRate).str(false),
                    months.take(months.count { m -> m.month.year == month.year && m.month <= month })
                        .avgOf { m -> (m.nettIncome div input.usdRate) }.str(false),
                    (BigDecimal.ONE - (if (grossIncome == zero) zero else nettIncome div grossIncome)).percentString(),
                ).mapIndexed { i, s -> "<td class=\"${cClass(i)}\">$s</td>" }
                    .joinToString("\n", prefix = "<tr>", postfix = "</tr>")
            }
        }

        val yearRow = with(months) {
            listOf(
                "$year",
                "Total",
                sumOf { it.workdays.bdc }.sc(0).str(),
                sumOf { it.sickDays.bdc }.sc(0).str(),
                sumOf { it.contractIncome }.str(false),
                sumOf { it.bonusesIncome }.str(false),
                sumOf { it.grossIncome }.str(false),
                (sumOf { it.grossIncome } div input.usdRate).str(false),
                sumOf { it.foodVouchersTotal }.str(false),
                sumOf { it.foodVouchersTaxable }.str(),
                sumOf { it.foodVouchersCompensation }.str(false),
                maxOf { it.yearlyZusBase }.str(),
                sumOf { it.zusBase }.str(),
                sumOf { it.retirementTax }.str(),
                sumOf { it.disabilityTax }.str(),
                sumOf { it.sicknessTax }.str(),
                sumOf { it.healthTax }.str(),
                sumOf { it.zus }.str(),
                maxOf { it.yearlyPitBase }.str(),
                sumOf { it.pitBase }.str(),
                sumOf { it.normalPIT }.str(),
                sumOf { it.increasedPIT }.str(),
                sumOf { it.pitTaxFree }.str(),
                sumOf { it.pitHealthReduce }.str(),
                sumOf { it.pit }.str(),
                sumOf { it.ppkTax }.str(),
                sumOf { it.nettDeductions }.str(),
                sumOf { it.nettIncome }.str(false),
                avgOf { it.nettIncome }.str(false),
                (sumOf { it.nettIncome } div input.usdRate).str(false),
                (avgOf { it.nettIncome } div input.usdRate).str(false),
                (BigDecimal.ONE - (if (sumOf { it.grossIncome } == zero) zero else sumOf { it.nettIncome } div sumOf { it.grossIncome })).percentString(),
            ).mapIndexed { i, s -> "<td class=\"${cClass(i)}\">$s</td>" }
                .joinToString("\n", prefix = "<tr class=\"has-text-weight-bold year-border\">", postfix = "</tr>")
        }

        monthRows + yearRow
    }

    val tableHtml = """
      <table class="table is-striped is-hoverable is-fullwidth">
        $header
        <tbody>
            $tableRows
        </tbody
      </table>
    """

    val logsHtml = taxMonths.joinToString("\n") { m ->
        """<div class="card is-fullwidth">
                <header class="card-header card-logs-toggle">
                    <a class="card-header-title">${m.month.year}, ${m.month.name}</a>
                    <a class="card-header-icon">
                        <span class="icon"><i class="fas fa-calendar-week"></i></i></span>
                    </a>
                </header>
                <div class="card-content p-1 is-hidden">
                    <div class="content">
                        <table class="table is-hoverable is-size-7 is-fullwidth">
                            ${m.log.joinToString("\n") { "<tr><td>$it</td></tr>" }}
                        </table>
                    </div>
                </div>
            </div>
        """.trimIndent()
    }

    val element = document.getElementById("output")
    element?.innerHTML = tableHtml

    document.getElementById("logs-container")?.innerHTML = logsHtml
    val cardToggles = document.getElementById("logs-container")
        ?.getElementsByClassName("card-logs-toggle")
        ?.asList() ?: emptyList()
    for (cardToggle in cardToggles) {
        cardToggle.addEventListener("click", { e ->
            val cardElement = (e.currentTarget as? HTMLElement)?.parentElement
            val htmlElement = cardElement?.childNodes?.get(3) as? HTMLElement
            htmlElement?.classList?.toggle("is-hidden")
        })
    }
}
