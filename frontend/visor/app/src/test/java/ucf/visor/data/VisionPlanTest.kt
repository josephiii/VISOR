package ucf.visor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ucf.visor.data.VisionPlan.HazardFocus
import ucf.visor.data.VisionPlan.Tailoring
import ucf.visor.lookaround.ScanPlan
import ucf.visor.stt.CameraCommand

/** How setup's answers about someone's vision change what VISOR does for them. */
class VisionPlanTest {

    private fun plan(
        vararg types: VisionType,
        severity: Severity = Severity.MODERATE,
        description: String = "",
        share: Boolean = true,
    ) = VisionPlan.from(
        UserProfile(
            visionTypes = if (types.isEmpty()) setOf(VisionType.NOT_SURE) else types.toSet(),
            severity = severity,
            visionDescription = description,
            shareVisionDescription = share,
        )
    )

    /** Someone with macular degeneration who can't read their mail. */
    private val maria = plan(VisionType.CENTRAL_LOSS, severity = Severity.SEVERE, description = "I can't read my mail")

    /** Someone with glaucoma who bumps into door frames and is bothered by sun. */
    private val dev = plan(VisionType.PERIPHERAL_LOSS, VisionType.CONTRAST_LIGHT)

    @Test
    fun skippingSetupChangesNothing() {
        val skipped = VisionPlan.from(UserProfile())
        assertTrue(skipped.isUntailored)
        assertEquals("", skipped.sceneGuidance)
        assertEquals(HazardFocus.NEAREST, skipped.hazardFocus)
        assertEquals(VisionPlan.BriefHazardLimit, skipped.briefHazardLimit)
        assertEquals(CameraCommand.entries, skipped.featureOrder)
        assertEquals(Verbosity.STANDARD, skipped.recommendedVerbosity)
        assertFalse(skipped.suggestsDarkHighContrast)
        assertEquals(emptyList<Tailoring>(), skipped.tailorings)
    }

    @Test
    fun twoDifferentUsersGetADifferentVisor() {
        assertNotEquals(maria.sceneGuidance, dev.sceneGuidance)
        assertNotEquals(maria.featureOrder.first(), dev.featureOrder.first())
        assertNotEquals(maria.hazardFocus, dev.hazardFocus)
        assertNotEquals(maria.suggestsDarkHighContrast, dev.suggestsDarkHighContrast)
        assertNotEquals(maria.recommendedVerbosity, dev.recommendedVerbosity)
    }

    @Test
    fun centerLossPutsReadingFirstAndWhatIsAheadFirst() {
        assertEquals(CameraCommand.READ_TEXT, maria.featureOrder[0])
        assertEquals(CameraCommand.IDENTIFY_OBJECTS, maria.featureOrder[1])
        assertEquals(HazardFocus.AHEAD_FIRST, maria.hazardFocus)
        assertTrue(maria.sceneGuidance.contains("directly ahead"))
    }

    @Test
    fun sideLossPutsHazardsFirstAndTheSidesFirst() {
        assertEquals(CameraCommand.CHECK_HAZARDS, dev.featureOrder[0])
        assertEquals(HazardFocus.SIDES_FIRST, dev.hazardFocus)
        assertTrue(dev.sceneGuidance.contains("left and right"))
    }

    @Test
    fun sideLossGetsTheWideLookAroundNextToHazards() {
        assertEquals(ScanPlan.WIDE, dev.lookAroundPlan)
        val sidesOnly = plan(VisionType.PERIPHERAL_LOSS)
        assertEquals(listOf(CameraCommand.CHECK_HAZARDS, CameraCommand.LOOK_AROUND), sidesOnly.featureOrder.take(2))
        assertTrue(Tailoring.WIDE_LOOK_AROUND in dev.tailorings)
        // Everyone else gets the standard scan, and no Look around line.
        assertEquals(ScanPlan.STANDARD, maria.lookAroundPlan)
        assertFalse(Tailoring.WIDE_LOOK_AROUND in maria.tailorings)
        assertEquals(ScanPlan.STANDARD, VisionPlan.from(UserProfile()).lookAroundPlan)
    }

    @Test
    fun blurPutsReadingFirstAndAsksForExactDetails() {
        val blur = plan(VisionType.BLUR_LOW_ACUITY)
        assertEquals(CameraCommand.READ_TEXT, blur.featureOrder.first())
        assertTrue(blur.sceneGuidance.contains("exact numbers"))
        assertEquals(listOf(Tailoring.READ_DETAILS), blur.tailorings)
    }

    @Test
    fun contrastAndLightPutsTheSceneFirstAndSuggestsTheDarkHighContrastLook() {
        val contrast = plan(VisionType.CONTRAST_LIGHT)
        assertEquals(CameraCommand.DESCRIBE_SCENE, contrast.featureOrder.first())
        assertTrue(contrast.sceneGuidance.contains("glare"))
        assertTrue(contrast.suggestsDarkHighContrast)
    }

    @Test
    fun theFeatureOrderAlwaysHasEveryFeatureOnce() {
        listOf(maria, dev, plan(VisionType.BLUR_LOW_ACUITY, VisionType.CONTRAST_LIGHT)).forEach {
            assertEquals(CameraCommand.entries.toSet(), it.featureOrder.toSet())
            assertEquals(CameraCommand.entries.size, it.featureOrder.size)
        }
    }

    @Test
    fun bothCenterAndSideLossHearsHazardsNearestFirst() {
        assertEquals(HazardFocus.NEAREST, plan(VisionType.CENTRAL_LOSS, VisionType.PERIPHERAL_LOSS).hazardFocus)
    }

    @Test
    fun severityChangesTheDetailSuggestedAndHowManyHazardsBriefNames() {
        assertEquals(Verbosity.DETAILED, maria.recommendedVerbosity)
        assertEquals(VisionPlan.SevereBriefHazardLimit, maria.briefHazardLimit)
        val mild = plan(severity = Severity.MILD)
        assertEquals(Verbosity.BRIEF, mild.recommendedVerbosity)
        assertTrue(mild.sceneGuidance.contains("skip what is obvious"))
        assertFalse(mild.isUntailored)
    }

    @Test
    fun theirOwnWordsAreQuotedOnlyWhileTheyShareThem() {
        assertTrue(maria.sceneGuidance.contains("\"I can't read my mail\""))
        assertTrue(Tailoring.OWN_WORDS in maria.tailorings)

        val private = plan(VisionType.CENTRAL_LOSS, description = "I can't read my mail", share = false)
        assertNull(private.ownWords)
        assertFalse(private.sceneGuidance.contains("mail"))
        assertFalse(Tailoring.OWN_WORDS in private.tailorings)
    }

    @Test
    fun theirOwnWordsAreOneLineWithoutDoubleQuotesAndCutAtAWord() {
        assertEquals("it's \"foggy\" in the morning".replace('"', '\''), VisionPlan.ownWords("it's \"foggy\"\n in the  morning"))
        assertNull(VisionPlan.ownWords("   \n "))

        val long = VisionPlan.ownWords("word ".repeat(200))!!
        assertTrue(long.length <= VisionPlan.OwnWordsMaxLength + 1)
        assertTrue(long.endsWith("word…"))
    }

    @Test
    fun namedConditionsSuggestTheKindOfLossTheyUsuallyBring() {
        assertEquals(
            mapOf(VisionType.PERIPHERAL_LOSS to "glaucoma"),
            VisionPlan.suggestedTypes("I have Glaucoma in both eyes.", setOf(VisionType.NOT_SURE)),
        )
        assertEquals(
            mapOf(VisionType.CENTRAL_LOSS to "macular degeneration"),
            VisionPlan.suggestedTypes("macular degeneration, the wet kind", emptySet()),
        )
        assertEquals(
            mapOf(VisionType.BLUR_LOW_ACUITY to "cataracts", VisionType.CONTRAST_LIGHT to "cataracts"),
            VisionPlan.suggestedTypes("cataracts", emptySet()),
        )
    }

    @Test
    fun aKindAlreadyChosenOrAWordInsideAnotherIsNotSuggested() {
        assertEquals(emptyMap<VisionType, String>(), VisionPlan.suggestedTypes("glaucoma", setOf(VisionType.PERIPHERAL_LOSS)))
        assertEquals(emptyMap<VisionType, String>(), VisionPlan.suggestedTypes("My dog Amdi helps me", emptySet()))
        assertEquals(emptyMap<VisionType, String>(), VisionPlan.suggestedTypes("", emptySet()))
    }
}
