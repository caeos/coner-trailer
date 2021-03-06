package org.coner.trailer.seasonpoints

import org.coner.trailer.Grouping
import org.coner.trailer.Person
import org.coner.trailer.Season
import org.coner.trailer.SeasonEvent
import org.coner.trailer.eventresults.ComprehensiveResultsReport
import org.coner.trailer.eventresults.GroupedResultsReport
import org.coner.trailer.eventresults.ResultsType
import java.util.*

class StandingsReportCreator {

    fun createComprehensiveStandingsReport(params: ComprehensiveStandingsReportParameters): StandingsReport {
        TODO()
    }

    class ComprehensiveStandingsReportParameters(
            val eventNumberToComprehensiveResultsReport: Map<Int, ComprehensiveResultsReport>
    )

    class CreateGroupedStandingsSectionsParameters(
            val resultsType: ResultsType,
            val season: Season,
            val eventToGroupedResultsReports: Map<SeasonEvent, GroupedResultsReport>,
            val configuration: SeasonPointsCalculatorConfiguration
    )

    fun createGroupedStandingsSections(
            param: CreateGroupedStandingsSectionsParameters
    ): SortedMap<Grouping, StandingsReport.Section> {
        val eventToCalculator: Map<SeasonEvent, EventPointsCalculator> = param.eventToGroupedResultsReports.keys.map { event: SeasonEvent ->
            val config = event.seasonPointsCalculatorConfiguration
                    ?: param.season.seasonPointsCalculatorConfiguration
            val eventPointsCalculator = config.resultsTypeToEventPointsCalculator[param.resultsType]
            checkNotNull(eventPointsCalculator) {
                "No event points calculator for results type: ${param.resultsType.title}"
            }
            event to eventPointsCalculator
        }.toMap()

        val groupingsToPersonStandingAccumulators: MutableMap<Grouping, MutableMap<Person, PersonStandingAccumulator>> = mutableMapOf()
        for ((event, groupedResultsReport) in param.eventToGroupedResultsReports) {
            val calculator = checkNotNull(eventToCalculator[event]) {
                "Failed to find season points calculator for event: ${event.event.name}"
            }
            for ((grouping, groupingResults) in groupedResultsReport.groupingsToResultsMap) {
                val accumulators: MutableMap<Person, PersonStandingAccumulator> = groupingsToPersonStandingAccumulators[grouping]
                        ?: mutableMapOf<Person, PersonStandingAccumulator>().apply {
                            groupingsToPersonStandingAccumulators[grouping] = this
                        }
                for (participantGroupingResult in groupingResults) {
                    if (participantGroupingResult.participant.person == null
                            || !participantGroupingResult.participant.seasonPointsEligible) {
                        continue
                    }
                    val accumulator = accumulators[participantGroupingResult.participant.person]
                            ?: PersonStandingAccumulator(
                                    person = participantGroupingResult.participant.person
                            ).apply {
                                accumulators[participantGroupingResult.participant.person] = this
                            }
                    with(accumulator) {
                        eventToPoints[event] = calculator.calculate(participantGroupingResult)
                        positionToFinishCount[participantGroupingResult.position] = (positionToFinishCount[participantGroupingResult.position] ?: 0).inc()
                    }
                }
            }
        }
        groupingsToPersonStandingAccumulators.forEach { (_, personToStandingAccumulators) ->
            personToStandingAccumulators.values.forEach { accumulator ->
                accumulator.score = accumulator.eventToPoints.values.sortedDescending()
                        .take(param.season.takeScoreCountForPoints ?: Int.MAX_VALUE)
                        .sum()
            }
        }
        val groupingsToFinalAccumulators = groupingsToPersonStandingAccumulators.map { (grouping, peopleStandingAccumulators) ->
            val finalAccumulators = peopleStandingAccumulators.values
                    .toList()
                    .sortedWith(param.configuration.rankingSort.comparator)
            grouping to finalAccumulators
        }.toMap()
        groupingsToFinalAccumulators.forEach { (_, accumulators) ->
            accumulators.mapIndexed { index, accumulator ->
                accumulator.position = if (index > 0) {
                    val previousAccumulator = accumulators[index - 1]
                    val comparison = param.configuration.rankingSort.comparator.compare(accumulator, previousAccumulator)
                    if (comparison != 0) {
                        checkNotNull(previousAccumulator.position) + 1
                    } else {
                        accumulator.tie = true
                        previousAccumulator.tie = true
                        previousAccumulator.position
                    }
                } else {
                    1
                }
            }
        }
        val sectionStandings: Map<Grouping, List<StandingsReport.Standing>> = groupingsToFinalAccumulators
                .map { (grouping, accumulators) ->
                    grouping to accumulators.map { accumulator ->
                        StandingsReport.Standing(
                                position = checkNotNull(accumulator.position),
                                tie = accumulator.tie,
                                person = accumulator.person,
                                eventToPoints = accumulator.eventToPoints.toSortedMap(),
                                score = accumulator.score
                        )
                    }.toList()
                }.toMap()
        return sectionStandings
                .map { (grouping, standings) ->
                    grouping to StandingsReport.Section(
                            title = grouping.abbreviation,
                            standings = standings
                    )
                }
                .toMap()
                .toSortedMap()
    }

}