package org.gtlv.atlas.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UserNameDisplayTest {
    @Test
    fun `short user name is unchanged`() {
        assertEquals(
            "Hubert Blaine",
            "Hubert Blaine".truncatedUserName()
        )
    }

    @Test
    fun `long user name is truncated with an ellipsis`() {
        assertEquals(
            "Hubert Blaine Wolfeschl…",
            "Hubert Blaine Wolfeschlegelsteinhausenbergerdorff"
                .truncatedUserName()
        )
    }

    @Test
    fun `custom limit allows a longer profile name`() {
        assertEquals(
            "Hubert Blaine Wolfeschlegelstei…",
            "Hubert Blaine Wolfeschlegelsteinhausenbergerdorff"
                .truncatedUserName(MAX_PROFILE_USER_NAME_LENGTH)
        )
    }

    @Test
    fun `surrogate pair is not split at truncation boundary`() {
        assertEquals(
            "1234567890123456789012🚀…",
            "1234567890123456789012🚀AB"
                .truncatedUserName()
        )
    }

    @Test
    fun `surrounding whitespace is removed`() {
        assertEquals(
            "Hubert Blaine",
            "  Hubert Blaine  ".truncatedUserName()
        )
    }
}
