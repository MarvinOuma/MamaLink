package com.mamalink.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DomainContractTest {
    @Test fun caregiverHasOptionalPhone() {
        val caregiver = Caregiver(id = 1L, fullName = "Sample Caregiver")
        assertEquals(1L, caregiver.id)
        assertNull(caregiver.phoneNumber)
    }

    @Test fun childPreservesBirthDateAndCaregiverRelationship() {
        val date = LocalDate.parse("2024-02-29")
        val child = Child(id = 2L, caregiverId = 1L, name = "Sample Child", dateOfBirth = date)
        assertEquals(date, child.dateOfBirth)
        assertEquals(1L, child.caregiverId)
    }

    @Test fun appointmentSupportsOptionalChildAndAbsoluteTime() {
        val time = Instant.parse("2026-12-01T09:00:00Z")
        val event = Appointment(id = 3L, caregiverId = 1L, title = "Sample visit", scheduledAt = time)
        assertEquals(time, event.scheduledAt)
        assertNull(event.childId)
        assertNull(event.location)
    }

    @Test fun appointmentWithChildRetainsAssociation() {
        val event = Appointment(
            id = 4L,
            caregiverId = 1L,
            childId = 2L,
            title = "Sample appointment",
            scheduledAt = Instant.parse("2026-12-02T11:30:00Z"),
            location = "Example facility",
        )
        assertEquals(2L, event.childId)
        assertEquals("Example facility", event.location)
    }
}
