package org.coner.trailer.cli.command.seasonpointscalculator

import com.github.ajalt.clikt.core.Abort
import com.github.ajalt.clikt.output.TermUi.echo
import org.coner.trailer.eventresults.ResultsType
import org.coner.trailer.eventresults.StandardResultsTypes
import org.coner.trailer.io.service.EventPointsCalculatorService
import org.coner.trailer.seasonpoints.EventPointsCalculator

class SeasonPointsCalculatorParameterMapper(
        private val eventPointsCalculatorService: EventPointsCalculatorService
) {

    fun fromParameter(parameter: List<Pair<String, String>>): Map<ResultsType, EventPointsCalculator> {
        return parameter.map { (resultsTypeKey, eventPointsCalculatorName) ->
            val resultsType = StandardResultsTypes.fromKey(resultsTypeKey)
            if (resultsType == null) {
                echo("Results type with key not found: $resultsTypeKey")
                throw Abort()
            }
            val eventPointsCalculator = eventPointsCalculatorService.findByName(eventPointsCalculatorName)
            if (eventPointsCalculator == null) {
                echo("Event points calculator with name not found: $eventPointsCalculatorName")
                throw Abort()
            }
            resultsType to eventPointsCalculator
        }.toMap()
    }

}