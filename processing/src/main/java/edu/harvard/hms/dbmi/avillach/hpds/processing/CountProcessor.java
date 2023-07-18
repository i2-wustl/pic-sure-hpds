package edu.harvard.hms.dbmi.avillach.hpds.processing;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import edu.harvard.hms.dbmi.avillach.hpds.data.phenotype.KeyAndValue;
import edu.harvard.hms.dbmi.avillach.hpds.data.query.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

import edu.harvard.hms.dbmi.avillach.hpds.data.query.Query;
import edu.harvard.hms.dbmi.avillach.hpds.exception.NotEnoughMemoryException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Component
public class CountProcessor implements HpdsProcessor {

	Logger log = LoggerFactory.getLogger(CountProcessor.class);

	private final AbstractProcessor abstractProcessor;

	@Autowired
	public CountProcessor(AbstractProcessor abstractProcessor) {
		this.abstractProcessor = abstractProcessor;
	}
	
	/**
	 * Count processor always returns same headers
	 */
	@Override
	public String[] getHeaderRow(Query query) {
		return new String[] {"Patient ID", "Count"};
	}

	/**
	 * Retrieves a list of patient ids that are valid for the query result and returns the size of that list.
	 * 
	 * @param query
	 * @return
	 */
	public int runCounts(Query query) {
		return abstractProcessor.getPatientSubsetForQuery(query).size();
	}

	/**
	 * Retrieves a list of patient ids that are valid for the query result and total number
	 * of observations recorded for all concepts included in the fields array for those patients.
	 * 
	 * @param query
	 * @return
	 */
	public int runObservationCount(Query query) {
		TreeSet<Integer> patients = abstractProcessor.getPatientSubsetForQuery(query);
		int[] observationCount = {0};
		query.getFields().stream().forEach(field -> {
			observationCount[0] += Arrays.stream(abstractProcessor.getCube(field).sortedByKey()).filter(keyAndValue->{
				return patients.contains(keyAndValue.getKey());
			}).count();
		});
		return observationCount[0];
	}
	
	/**
	 * Returns a separate observation count for each field in query.crossCountFields when that field is added
	 * as a requiredFields entry for the base query.
	 * 
	 * @param query
	 * @return
	 */
	public Map<String, Integer> runObservationCrossCounts(Query query) {
		TreeMap<String, Integer> counts = new TreeMap<>();
		TreeSet<Integer> baseQueryPatientSet = abstractProcessor.getPatientSubsetForQuery(query);
		query.getCrossCountFields().parallelStream().forEach((String concept)->{
			try {
				//breaking these statements to allow += operator to cast long to int.
				int observationCount = 0;
				observationCount += (Long) Arrays.stream(abstractProcessor.getCube(concept).sortedByKey()).filter(keyAndValue -> {
					return baseQueryPatientSet.contains(keyAndValue.getKey());
				}).count();
				counts.put(concept, observationCount);
			} catch (Exception e) {
				counts.put(concept, -1);
			}
		});
		return counts;
	}

	/**
	 * Returns a separate count for each field in query.crossCountFields when that field is added
	 * as a requiredFields entry for the base query.
	 * 
	 * @param query
	 * @return
	 */
	public Map<String, Integer> runCrossCounts(Query query) {
		TreeMap<String, Integer> counts = new TreeMap<>();
		TreeSet<Integer> baseQueryPatientSet = abstractProcessor.getPatientSubsetForQuery(query);
		query.getCrossCountFields().parallelStream().forEach((String concept)->{
			try {
				Query safeCopy = new Query();
				safeCopy.setRequiredFields(List.of(concept));
				counts.put(concept, Sets.intersection(abstractProcessor.getPatientSubsetForQuery(safeCopy), baseQueryPatientSet).size());
			} catch (Exception e) {
				counts.put(concept, -1);
			}
		});
		return counts;
	}

	/**
	 * Returns a separate count for each field in the requiredFields and categoryFilters query.
	 *
	 * @param query
	 * @return a map of categorical data and their counts
	 */
	public  Map<String, Map<String, Integer>> runCategoryCrossCounts(Query query) {
		Map<String, Map<String, Integer>> categoryCounts = new TreeMap<>();
		TreeSet<Integer> baseQueryPatientSet = abstractProcessor.getPatientSubsetForQuery(query);
		query.getRequiredFields().parallelStream().forEach(concept -> {
			Map<String, Integer> varCount = new TreeMap<>();;
			TreeMap<String, TreeSet<Integer>> categoryMap = abstractProcessor.getCube(concept).getCategoryMap();
			//We do not have all the categories (aka variables) for required fields, so we need to get them and
			// then ensure that our base patient set, which is filtered down by our filters. Which may include
			// not only other required filters, but categorical filters, numerical filters, or genomic filters.
			// We then need to get the amount a patients for each category and map that to the concept path.
			categoryMap.forEach((String category, TreeSet<Integer> patientSet)->{
				//If all the patients are in the base then no need to loop, this would always be true for single
				// filter queries.
				if (baseQueryPatientSet.containsAll(patientSet)) {
					varCount.put(category, patientSet.size());
				} else {
					for (Integer patient : patientSet) {
						if (baseQueryPatientSet.contains(patient)) {
							// If we have a patient in the base set, we add 1 to the count.
							// We are only worried about patients in the base set
							varCount.put(category, varCount.getOrDefault(category, 1) + 1);
						} else {
							// If we don't have a patient in the base set, we add 0 to the count.
							// This is necessary because we need to ensure that all categories are included in the
							// map, even if they have a count of 0. This is because we are displaying the counts
							// in a table (or other form).
							varCount.put(category, varCount.getOrDefault(category, 0));
						}
					}
				}
			});
			categoryCounts.put(concept, varCount);
		});
		//For categoryFilters we need to ensure the variables included in the filter are the ones included in our count
		//map. Then we make sure that the patients who have that variable are also in our base set.
		query.getCategoryFilters().entrySet().parallelStream().forEach(categoryFilterEntry-> {
			Map<String, Integer> varCount;
			TreeMap<String, TreeSet<Integer>> categoryMap = abstractProcessor.getCube(categoryFilterEntry.getKey()).getCategoryMap();
			varCount = new TreeMap<>();
			categoryMap.forEach((String category, TreeSet<Integer> patientSet)->{
				if (Arrays.asList(categoryFilterEntry.getValue()).contains(category)) {
					varCount.put(category, Sets.intersection(patientSet, baseQueryPatientSet).size());
				}
			});
			categoryCounts.put(categoryFilterEntry.getKey(), varCount);
		});
		return categoryCounts;
	}

	/**
	 * Returns a separate count for each range in numericFilters in query.
	 *
	 * @param query
	 * @return a map of numerical data and their counts
	 */
	public Map<String, Map<Double, Integer>> runContinuousCrossCounts(Query query) {
		TreeMap<String, Map<Double, Integer>> conceptMap = new TreeMap<>();
		TreeSet<Integer> baseQueryPatientSet = abstractProcessor.getPatientSubsetForQuery(query);
		query.getNumericFilters().forEach((String concept, Filter.DoubleFilter range)-> {
			KeyAndValue[] pairs = abstractProcessor.getCube(concept).getEntriesForValueRange(range.getMin(), range.getMax());
			Map<Double, Integer> countMap = new TreeMap<>();
			Arrays.stream(pairs).forEach(patientConceptPair -> {
				//The key of the patientConceptPair is the patient id. We need to make sure the patient matches our query.
				if (baseQueryPatientSet.contains(patientConceptPair.getKey())) {
					if (countMap.containsKey(patientConceptPair.getValue())) {
						countMap.put((double)patientConceptPair.getValue(), countMap.get(patientConceptPair.getValue()) + 1);
					} else {
						countMap.put((double)patientConceptPair.getValue(), 1);
					}
				}
			});
			conceptMap.put(concept, countMap);
		});
		return conceptMap;
	}

	/**
	 * Until we have a count based query that takes longer than 30 seconds to run, we should discourage
	 * running them asynchronously in the backend as this results in unnecessary request-response cycles.
	 */
	@Override
	public void runQuery(Query query, AsyncResult asyncResult) {
		throw new UnsupportedOperationException("Counts do not run asynchronously.");
	}

	/**
	 * Process only variantInfoFilters to count the number of variants that would be included in evaluating the query.
	 * 
	 * This does not actually evaluate a patient set for the query.
	 * 
	 * @param query
	 * @return the number of variants that would be used to filter patients if the incomingQuery was run as a COUNT query.
	 */
	public Map<String, Object> runVariantCount(Query query) {
		TreeMap<String, Object> response = new TreeMap<String, Object>();
		if(!query.getVariantInfoFilters().isEmpty()) {
			try {
				response.put("count", abstractProcessor.getVariantList(query).size());
			} catch (IOException e) {
				log.error("Error processing query", e);
				response.put("count", "0");
				response.put("message", "An unexpected error occurred while processing the query, please contact us to let us know using the Contact Us option in the Help menu.");
			}
			response.put("message", "Query ran successfully");
		} else {
			response.put("count", "0");
			response.put("message", "No variant filters were supplied, so no query was run.");
		}
		return response;
	}
}
