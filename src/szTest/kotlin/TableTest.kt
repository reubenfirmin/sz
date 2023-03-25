import view.HEADING_IDX
import view.Table
import kotlin.test.Test
import kotlin.test.assertEquals

class TableTest {

    @Test
    fun `wide rows on terminals have to wrap`() {
        // if a column can't fit in a row it just gets cut off
        val table = Table(false, 30)
            .column("foo", width = 10)
            .column("bah", width = 8)
            .column("goat", width = 8)
            .column("cow", consumeRemainingWidth = true)

        assertEquals(table.columns[0].width, 10)
        assertEquals(table.columns[1].width, 8)
        assertEquals(table.columns[2].width, 8)
        assertEquals(table.columns[3].width, 0)

        val heading = table.row(HEADING_IDX, arrayOf("there", "was", "a", "man"))

        assertEquals(" there      was      a       ", heading[0])
    }
}