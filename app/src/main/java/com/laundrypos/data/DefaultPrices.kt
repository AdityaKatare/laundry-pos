package com.laundrypos.data

object DefaultPrices {
    private const val WASH_IRON = "Wash & Iron"
    private const val DRY_CLEAN = "Dry Clean"
    private const val PRESS = "Steam Press"
    private const val WASH_FOLD = "Wash & Fold"

    val items: List<PriceItem> = listOf(
        PriceItem(name = "Shirt", service = WASH_IRON, unit = PricingUnit.PIECE, ratePaise = 40_00),
        PriceItem(name = "T-Shirt", service = WASH_IRON, unit = PricingUnit.PIECE, ratePaise = 35_00),
        PriceItem(name = "Trouser", service = WASH_IRON, unit = PricingUnit.PIECE, ratePaise = 45_00),
        PriceItem(name = "Jeans", service = WASH_IRON, unit = PricingUnit.PIECE, ratePaise = 50_00),
        PriceItem(name = "Kurta", service = WASH_IRON, unit = PricingUnit.PIECE, ratePaise = 50_00),
        PriceItem(name = "Bedsheet", service = WASH_IRON, unit = PricingUnit.PIECE, ratePaise = 80_00),
        PriceItem(name = "Shirt", service = DRY_CLEAN, unit = PricingUnit.PIECE, ratePaise = 90_00),
        PriceItem(name = "Trouser", service = DRY_CLEAN, unit = PricingUnit.PIECE, ratePaise = 100_00),
        PriceItem(name = "Saree", service = DRY_CLEAN, unit = PricingUnit.PIECE, ratePaise = 250_00),
        PriceItem(name = "Suit (2 pc)", service = DRY_CLEAN, unit = PricingUnit.PIECE, ratePaise = 350_00),
        PriceItem(name = "Blazer", service = DRY_CLEAN, unit = PricingUnit.PIECE, ratePaise = 250_00),
        PriceItem(name = "Blanket", service = DRY_CLEAN, unit = PricingUnit.PIECE, ratePaise = 400_00),
        PriceItem(name = "Shirt", service = PRESS, unit = PricingUnit.PIECE, ratePaise = 15_00),
        PriceItem(name = "Trouser", service = PRESS, unit = PricingUnit.PIECE, ratePaise = 15_00),
        PriceItem(name = "Saree", service = PRESS, unit = PricingUnit.PIECE, ratePaise = 60_00),
        PriceItem(name = "Mixed clothes", service = WASH_FOLD, unit = PricingUnit.KG, ratePaise = 80_00),
        PriceItem(name = "Mixed clothes", service = "Wash & Iron (kg)", unit = PricingUnit.KG, ratePaise = 120_00),
    )
}
