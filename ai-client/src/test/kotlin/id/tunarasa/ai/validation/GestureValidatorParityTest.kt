package id.tunarasa.ai.validation

import id.tunarasa.ai.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Test

/** Expected values come from the Python service (see tools/generate-validation-fixture.py). */
class GestureValidatorParityTest {

    @Test
    fun `every case matches the Python service`() {
        val validator = GestureValidator()
        val rows = Fixtures.lines("validation_parity.tsv")
        assertEquals(1134, rows.size)
        for (row in rows) {
            val (text, confidence, correct, similarity, accuracy) = row.split('\t')
            val matchId = row.split('\t')[5]
            val actual = validator.validate(text, confidence.toDouble())
            assertEquals(row, correct == "1", actual.isCorrect)
            assertEquals(row, similarity.toDouble(), actual.similarity, 1e-9)
            assertEquals(row, accuracy.toDouble(), actual.accuracyScore, 1e-9)
            assertEquals(row, matchId, actual.match?.id.orEmpty())
        }
    }

    @Test
    fun `dataset is complete and ids are unique`() {
        assertEquals(22, GroundTruthDataset.ALL.map { it.id }.toSet().size)
    }

    @Test
    fun `empty prediction is incorrect`() {
        assertEquals(GestureValidation(false, 0.0, 0.0, null), GestureValidator().validate("", 1.0))
    }
}
