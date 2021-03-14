package org.coner.trailer.io.verification

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.coner.trailer.*
import org.coner.trailer.datasource.crispyfish.CrispyFishEventMappingContext
import org.coner.trailer.datasource.crispyfish.CrispyFishParticipantMapper
import org.coner.trailer.datasource.crispyfish.TestRegistrations
import org.coner.trailer.io.service.MotorsportRegPeopleMapService
import org.coner.trailer.io.service.PersonService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class EventCrispyFishPersonMapVerifierTest {

    lateinit var verifier: EventCrispyFishPersonMapVerifier

    @MockK lateinit var personService: PersonService
    @MockK lateinit var crispyFishParticipantMapper: CrispyFishParticipantMapper
    @MockK lateinit var motorsportRegPeopleMapService: MotorsportRegPeopleMapService

    @MockK lateinit var context: CrispyFishEventMappingContext
    @MockK lateinit var callback: EventCrispyFishPersonMapVerifier.Callback

    @BeforeEach
    fun before() {
        verifier = EventCrispyFishPersonMapVerifier(
            personService = personService,
            crispyFishParticipantMapper = crispyFishParticipantMapper,
            motorsportRegPeopleMapService = motorsportRegPeopleMapService
        )
    }

    @Test
    fun `When person mapped, it should invoke onMapped`() {
        val person = TestPeople.REBECCA_JACKSON
        val registration = TestRegistrations.Lscc2019Points1.REBECCA_JACKSON.copy(
            memberNumber = null
        )
        val participant = TestParticipants.Lscc2019Points1.REBECCA_JACKSON.copy(
            person = null
        )
        val key = Event.CrispyFishMetadata.PeopleMapKey(
            grouping = requireNotNull(participant.signage?.grouping),
            number = requireNotNull(participant.signage?.number),
            firstName = requireNotNull(registration.firstName),
            lastName = requireNotNull(registration.lastName)
        )
        val event: Event = mockk {
            every { crispyFish } returns mockk {
                every { peopleMap } returns mapOf(key to person)
            }
        }
        val allRegistrations = listOf(registration)
        every { context.allRegistrations } returns allRegistrations
        every { personService.list() } returns listOf(person)
        every {
            crispyFishParticipantMapper.toCoreSignage(
                context = context,
                crispyFish = registration
            )
        } returns requireNotNull(participant.signage)
        justRun { callback.onMapped(any(), any()) }

        verifier.verify(
            event = event,
            context = context,
            callback = callback
        )

        verifySequence {
            personService.list()
            crispyFishParticipantMapper.toCoreSignage(
                context = context,
                crispyFish = registration
            )
            callback.onMapped(registration, key to person)
        }
    }

    @Test
    fun `When no person mapped but registration can cross-reference to person via motorsportreg it should invoke such`() {
        val person = TestPeople.REBECCA_JACKSON
        every { personService.list() } returns listOf(person)
        val participant = TestParticipants.Lscc2019Points1.REBECCA_JACKSON
        val registration = TestRegistrations.Lscc2019Points1.REBECCA_JACKSON
        every { context.allRegistrations } returns listOf(registration)
        val crossReference = Event.CrispyFishMetadata.PeopleMapKey(
            grouping = requireNotNull(participant.signage?.grouping),
            number = requireNotNull(participant.signage?.number),
            firstName = requireNotNull(participant.firstName),
            lastName = requireNotNull(participant.lastName)
        ) to person
        every { motorsportRegPeopleMapService.assemble(any(), any()) } returns mapOf(crossReference)
        every { crispyFishParticipantMapper.toCoreSignage(context, registration) } returns requireNotNull(participant.signage)
        val event: Event = mockk {
            every { crispyFish } returns mockk {
                every { peopleMap } returns emptyMap()
            }
        }
        justRun { callback.onUnmappedMotorsportRegPersonExactMatch(any(), any()) }

        verifier.verify(
            event = event,
            context = context,
            callback = callback
        )

        verifySequence {
            personService.list()
            crispyFishParticipantMapper.toCoreSignage(context, registration)
            callback.onUnmappedMotorsportRegPersonExactMatch(registration, crossReference)
        }
    }

    @Test
    fun `When no person mapped and registration lacks club member ID it should invoke onUnmappedClubMemberIdNull`() {
        val registration = TestRegistrations.Lscc2019Points1.REBECCA_JACKSON.copy(
            memberNumber = null
        )
        val participant = TestParticipants.Lscc2019Points1.REBECCA_JACKSON.copy(
            person = null
        )
        val person = TestPeople.REBECCA_JACKSON
        val event: Event = mockk {
            every { crispyFish } returns mockk {
                every { peopleMap } returns emptyMap()
            }
        }
        val allRegistrations = listOf(registration)
        every { context.allRegistrations } returns allRegistrations
        every { personService.list() } returns listOf(person)
        every { motorsportRegPeopleMapService.assemble(any(), any()) } returns emptyMap()
        every {
            crispyFishParticipantMapper.toCoreSignage(
                context = context,
                crispyFish = registration
            )
        } returns requireNotNull(participant.signage)
        justRun { callback.onUnmappedClubMemberIdNull(any()) }

        verifier.verify(
            event = event,
            context = context,
            callback = callback
        )

        verifySequence {
            personService.list()
            crispyFishParticipantMapper.toCoreSignage(
                context = context,
                crispyFish = registration
            )
            callback.onUnmappedClubMemberIdNull(registration)
        }
    }

    @Test
    fun `When no person mapped and registration's club member ID doesn't match any people, it should invoke onUnmappedClubMemberIdNotFound`() {
        val registration = TestRegistrations.Lscc2019Points1.REBECCA_JACKSON
        val participant = TestParticipants.Lscc2019Points1.REBECCA_JACKSON.copy(
            person = null
        )
        val event: Event = mockk {
            every { crispyFish } returns mockk {
                every { peopleMap } returns emptyMap()
            }
        }
        val allRegistrations = listOf(registration)
        every { context.allRegistrations } returns allRegistrations
        every { personService.list() } returns emptyList()
        every { motorsportRegPeopleMapService.assemble(any(), any()) } returns emptyMap()
        every {
            crispyFishParticipantMapper.toCoreSignage(
                context = context,
                crispyFish = registration
            )
        } returns requireNotNull(participant.signage)
        justRun { callback.onUnmappedClubMemberIdNotFound(any()) }

        verifier.verify(
            event = event,
            context = context,
            callback = callback
        )

        verifySequence {
            personService.list()
            crispyFishParticipantMapper.toCoreSignage(
                context = context,
                crispyFish = registration
            )
            callback.onUnmappedClubMemberIdNotFound(registration)
        }
    }

    @Test
    fun `When no person mapped and registration club member ID matches multiple people, it should invoke onUnmappedClubMemberIdAmbiguous`() {
        val registrations = listOf(
            TestRegistrations.Lscc2019Points1.REBECCA_JACKSON,
            TestRegistrations.Lscc2019Points1.BRANDY_HUFF.copy(
                memberNumber = TestRegistrations.Lscc2019Points1.REBECCA_JACKSON.memberNumber
            )
        )
        val participants = listOf(
            TestParticipants.Lscc2019Points1.REBECCA_JACKSON.copy(
                person = null
            ),
            TestParticipants.Lscc2019Points1.BRANDY_HUFF.copy(
                person = null
            )
        )
        val people = listOf(
            TestPeople.REBECCA_JACKSON,
            TestPeople.BRANDY_HUFF.copy(
                clubMemberId = TestPeople.REBECCA_JACKSON.clubMemberId
            )
        )
        val event: Event = mockk {
            every { crispyFish } returns mockk {
                every { peopleMap } returns emptyMap()
            }
        }
        every { context.allRegistrations } returns registrations
        every { personService.list() } returns people
        every { motorsportRegPeopleMapService.assemble(any(), any()) } returns emptyMap()
        every {
            crispyFishParticipantMapper.toCoreSignage(
                context = context,
                crispyFish = registrations[0]
            )
        } returns requireNotNull(participants[0].signage)
        every {
            crispyFishParticipantMapper.toCoreSignage(
                context = context,
                crispyFish = registrations[1]
            )
        } returns requireNotNull(participants[1].signage)
        justRun { callback.onUnmappedClubMemberIdAmbiguous(any(), any()) }

        verifier.verify(
            event = event,
            context = context,
            callback = callback
        )

        verifySequence {
            personService.list()
            crispyFishParticipantMapper.toCoreSignage(
                context = context,
                crispyFish = registrations[0]
            )
            callback.onUnmappedClubMemberIdAmbiguous(registrations[0], people)
            crispyFishParticipantMapper.toCoreSignage(
                context = context,
                crispyFish = registrations[1]
            )
            callback.onUnmappedClubMemberIdAmbiguous(registrations[1], people)
        }
    }

    @Test
    fun `When no person mapped and registration matches a club member ID but not first or last name, it should invoke onUnmappedClubMemberIdMatchButNameMismatch`() {
        val people = listOf(
            TestPeople.REBECCA_JACKSON,
            TestPeople.BRANDY_HUFF
        )
        every { personService.list() } returns people
        val participants = listOf(
            TestParticipants.Lscc2019Points1.REBECCA_JACKSON,
            TestParticipants.Lscc2019Points1.BRANDY_HUFF
        )
        val registrations = listOf(
            TestRegistrations.Lscc2019Points1.REBECCA_JACKSON.copy(firstName = "Not Rebecca"),
            TestRegistrations.Lscc2019Points1.BRANDY_HUFF.copy(firstName = "Not Brandy")
        )
        every { context.allRegistrations } returns registrations
        every { motorsportRegPeopleMapService.assemble(any(), any()) } returns emptyMap()
        every { crispyFishParticipantMapper.toCoreSignage(context, registrations[0]) } returns requireNotNull(participants[0].signage)
        every { crispyFishParticipantMapper.toCoreSignage(context, registrations[1]) } returns requireNotNull(participants[1].signage)
        val event: Event = mockk {
            every { crispyFish } returns mockk {
                every { peopleMap } returns emptyMap()
            }
        }
        justRun { callback.onUnmappedClubMemberIdMatchButNameMismatch(any(), any()) }

        verifier.verify(
            event = event,
            context = context,
            callback = callback
        )

        verifySequence {
            personService.list()
            crispyFishParticipantMapper.toCoreSignage(context, registrations[0])
            callback.onUnmappedClubMemberIdMatchButNameMismatch(registrations[0], people[0])
            crispyFishParticipantMapper.toCoreSignage(context, registrations[1])
            callback.onUnmappedClubMemberIdMatchButNameMismatch(registrations[1], people[1])
        }
    }

    @Test
    fun `When no person mapped and registration club member ID and first and last name matches single person, it should invoke onUnmappedExactMatch`() {
        val person = TestPeople.REBECCA_JACKSON
        every { personService.list() } returns listOf(person)
        val participant = TestParticipants.Lscc2019Points1.REBECCA_JACKSON
        val registration = TestRegistrations.Lscc2019Points1.REBECCA_JACKSON
        every { context.allRegistrations } returns listOf(registration)
        every { motorsportRegPeopleMapService.assemble(any(), any()) } returns emptyMap()
        every { crispyFishParticipantMapper.toCoreSignage(context, registration) } returns requireNotNull(participant.signage)
        val event: Event = mockk {
            every { crispyFish } returns mockk {
                every { peopleMap } returns emptyMap()
            }
        }
        justRun { callback.onUnmappedExactMatch(registration, person) }

        verifier.verify(
            event = event,
            context = context,
            callback = callback
        )

        verifySequence {
            personService.list()
            crispyFishParticipantMapper.toCoreSignage(context, registration)
            callback.onUnmappedExactMatch(registration, person)
        }
    }

    @Test
    fun `When there is an unused mapping, it should invoke onUnused`() {
        val person = TestPeople.REBECCA_JACKSON
        every { personService.list() } returns emptyList()
        val participant = TestParticipants.Lscc2019Points1.REBECCA_JACKSON
        val registration = TestRegistrations.Lscc2019Points1.REBECCA_JACKSON
        every { context.allRegistrations } returns emptyList()
        val key = Event.CrispyFishMetadata.PeopleMapKey(
            grouping = requireNotNull(participant.signage?.grouping),
            number = requireNotNull(participant.signage?.number),
            firstName = requireNotNull(registration.firstName),
            lastName = requireNotNull(registration.lastName)
        )
        val event: Event = mockk {
            every { crispyFish } returns mockk {
                every { peopleMap } returns mapOf(key to person)
            }
        }
        justRun { callback.onUnused(any(), any()) }

        verifier.verify(
            event = event,
            context = context,
            callback = callback
        )

        verifySequence {
            personService.list()
            callback.onUnused(key, person)
        }
        confirmVerified(crispyFishParticipantMapper)
    }
}