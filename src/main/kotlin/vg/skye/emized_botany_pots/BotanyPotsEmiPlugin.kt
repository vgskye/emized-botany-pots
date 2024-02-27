package vg.skye.emized_botany_pots

import dev.emi.emi.api.EmiPlugin
import dev.emi.emi.api.EmiRegistry
import dev.emi.emi.api.recipe.EmiRecipe
import dev.emi.emi.api.recipe.EmiRecipeCategory
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import dev.emi.emi.api.widget.WidgetHolder
import net.darkhax.botanypots.BotanyPotHelper
import net.darkhax.botanypots.data.recipes.crop.BasicCrop
import net.darkhax.botanypots.data.recipes.soil.BasicSoil
import net.minecraft.ChatFormatting
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.crafting.Ingredient
import java.text.DecimalFormat
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.roundToLong


class BotanyPotsEmiPlugin: EmiPlugin {
    companion object {
        private val SOIL_CATEGORY = EmiRecipeCategory(
            ResourceLocation("emized-botany-pots", "botanypots_soil"),
            EmiStack.of(BuiltInRegistries.ITEM.get(ResourceLocation("botanypots", "terracotta_botany_pot")))
        )
        private val CROP_CATEGORY = EmiRecipeCategory(
            ResourceLocation("emized-botany-pots", "botanypots_crop"),
            EmiStack.of(BuiltInRegistries.ITEM.get(ResourceLocation("botanypots", "terracotta_botany_pot")))
        )
        private val FORMAT = DecimalFormat("#.##")
    }
    override fun register(registry: EmiRegistry) {
        registry.addCategory(SOIL_CATEGORY)
        registry.addWorkstation(SOIL_CATEGORY, EmiIngredient.of(TagKey.create(Registries.ITEM, ResourceLocation("botanypots", "all_botany_pots"))))
        registry.addCategory(CROP_CATEGORY)
        registry.addWorkstation(CROP_CATEGORY, EmiIngredient.of(TagKey.create(Registries.ITEM, ResourceLocation("botanypots", "all_botany_pots"))))

        val manager = registry.recipeManager
        val soils = BotanyPotHelper
            .getAllRecipes(manager, BotanyPotHelper.SOIL_TYPE.get())
            .filterIsInstance(BasicSoil::class.java)
        val crops = BotanyPotHelper
            .getAllRecipes(manager, BotanyPotHelper.CROP_TYPE.get())
            .filterIsInstance(BasicCrop::class.java)

        for (soil in soils) {
            registry.addRecipe(BotanyPotsSoilEmiRecipe(soil))
        }

        for (crop in crops) {
            registry.addRecipe(BotanyPotsCropEmiRecipe(
                crop,
                soils.filter { soil ->
                    crop.canGrowInSoil(null, null, null, soil)
                }
            ))
        }
    }

    class BotanyPotsSoilEmiRecipe(private val soil: BasicSoil) : EmiRecipe {
        override fun getCategory(): EmiRecipeCategory = SOIL_CATEGORY

        override fun getId(): ResourceLocation = soil.id

        override fun getInputs(): List<EmiIngredient> = listOf(EmiIngredient.of(soil.ingredient))

        override fun getOutputs(): List<EmiStack> = listOf()

        override fun getDisplayWidth(): Int = 144

        override fun getDisplayHeight(): Int = 18

        override fun supportsRecipeTree(): Boolean = false

        override fun addWidgets(widgets: WidgetHolder) {
            widgets.addSlot(EmiIngredient.of(soil.ingredient), 0, 0).recipeContext(this)
            widgets.addText(
                Component.translatable("tooltip.botanypots.modifier", FORMAT.format(soil.growthModifier)),
                20, 5, -1, true
            )
        }
    }

    class BotanyPotsCropEmiRecipe(private val crop: BasicCrop, private val soils: List<BasicSoil>) : EmiRecipe {
        override fun getCategory(): EmiRecipeCategory = CROP_CATEGORY

        override fun getId(): ResourceLocation = crop.id

        override fun getInputs(): List<EmiIngredient> = listOf(
            EmiIngredient.of(crop.seed),
            EmiIngredient.of(soils.map { soil ->
                EmiIngredient.of(soil.ingredient)
            })
        )

        override fun getOutputs(): List<EmiStack> = crop.results.map { entry ->
            EmiStack.of(entry.item).apply {
                chance = entry.chance
                amount = (amount * (entry.maxRolls + entry.minRolls) / 2.0).roundToLong()
            }
        }

        override fun getDisplayWidth(): Int = 128

        override fun getDisplayHeight(): Int = max(
            36,
            18 * ((crop.results.size - 1) / 4 + 1)
        )

        override fun supportsRecipeTree(): Boolean = false

        override fun addWidgets(widgets: WidgetHolder) {
            widgets
                .addSlot(EmiIngredient.of(crop.seed), 0, (widgets.height - 36) / 2)
                .appendTooltip(Component
                    .translatable(
                        "tooltip.botanypots.grow_time",
                        ticksToTime(crop.growthTicks)
                    )
                    .withStyle(ChatFormatting.GRAY)
                )
                .catalyst(true)
            widgets
                .addSlot(EmiIngredient.of(soils.map { soil ->
                    EmiIngredient.of(soil.ingredient)
                }, 1), 0, (widgets.height - 36) / 2 + 18)
                .catalyst(true)

            widgets.addFillingArrow(24, (widgets.height - 18) / 2, 50 * crop.growthTicks)

            for ((index, result) in crop.results.withIndex()) {
                widgets.addSlot(
                    EmiIngredient.of(
                        Ingredient.of(result.item),
                        (result.item.count * (result.minRolls + result.maxRolls) / 2.0).roundToLong()
                    ).setChance(result.chance),
                    56 + 18 * (index % 4),
                    if (crop.results.size <= 4)
                        9
                    else
                        18 * (index / 4)
                ).apply {
                    if (result.minRolls != result.maxRolls)
                        appendTooltip(
                            Component
                                .translatable(
                                    "tooltip.botanypots.rollrange",
                                    result.minRolls, result.maxRolls
                                )
                                .withStyle(ChatFormatting.GRAY)
                        )
                    recipeContext(this@BotanyPotsCropEmiRecipe)
                }
            }
        }

        private fun ticksToTime(ticks: Int): String {
            var seconds = ticks.absoluteValue / 20
            val minutes = seconds / 60
            seconds %= 60
            return if (seconds < 10) "$minutes:0$seconds" else "$minutes:$seconds"
        }
    }
}