package com.replymate.data

import com.replymate.ai.Mood
import com.replymate.ai.ReplyLength
import com.replymate.ai.ReplyOptions
import com.replymate.ai.Vibe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonasAndContactsTest {
    private val legacy = Persona(aboutMe = "old about", examples = "a\nb", replyLanguage = ReplyLanguage.ENGLISH)

    @Test
    fun migratesPre12SinglePersona() {
        val list = PersonaStore.decode(null, legacy)
        assertEquals(listOf(legacy), list)
        assertEquals(DEFAULT_PERSONA_ID, list.single().id)
        assertEquals(listOf(legacy), PersonaStore.decode("not json", legacy))
        assertEquals(listOf(legacy), PersonaStore.decode("[]", legacy))
    }

    @Test
    fun personasRoundTrip() {
        val ps = listOf(legacy, Persona(id = "p2", label = "Playful Planner", writingStyle = "fun", replyLanguage = ReplyLanguage.MIXED))
        assertEquals(ps, PersonaStore.decode(PersonaStore.encode(ps), Persona()))
    }

    @Test
    fun personaLookupFallsBackToDefault() {
        val s = AppSettings(personas = listOf(Persona(id = "a", label = "A"), Persona(id = "b", label = "B")), defaultPersonaId = "b")
        assertEquals("B", s.defaultPersona.label)
        assertEquals("A", s.persona("a").label)
        assertEquals("B", s.persona("deleted").label)
        assertEquals("B", s.persona(null).label)
    }

    @Test
    fun contactsRoundTripKeyedCaseInsensitively() {
        val c = ContactProfile(
            name = "Grace  Jolly",
            note = "crush, keep it flirty",
            personaId = "p2",
            options = ReplyOptions(listOf(Mood.FLIRTY), ReplyLength.SHORT, Vibe.PLAYFUL),
        )
        val map = PersonaStore.decodeContacts(PersonaStore.encodeContacts(listOf(c)))
        assertEquals(c, map[contactKey("grace jolly")])
        assertEquals("grace jolly", c.key)
        assertTrue(PersonaStore.decodeContacts("garbage").isEmpty())
    }

    @Test
    fun rememberStoresAndClearsButKeepsNote() {
        val style = ReplyOptions(listOf(Mood.LIGHT))
        val created = ContactProfiles.applyRemember(null, "Grace", remember = true, personaId = "p2", options = style)!!
        assertEquals(ContactProfile("Grace", personaId = "p2", options = style), created)

        val withNote = created.copy(note = "my crush")
        val cleared = ContactProfiles.applyRemember(withNote, "Grace", remember = false, personaId = "p2", options = style)!!
        assertEquals("my crush", cleared.note)
        assertNull(cleared.personaId)
        assertNull(cleared.options)

        // Nothing left (no note, nothing remembered): profile is removed.
        assertNull(ContactProfiles.applyRemember(created, "Grace", remember = false, personaId = "p2", options = style))
        assertNull(ContactProfiles.applyRemember(null, "Grace", remember = false, personaId = "p2", options = style))
    }
}
