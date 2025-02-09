package edu.harvard.hms.dbmi.avillach.hpds.data.phenotype;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

/**
 * This provides a common interface for csv and sql load jobs, which use different indexes.
 * @author nchu
 *
 */
public class PhenoInput {

	private int patientNum;
	private String conceptPath;
	private String numericValue;
	private String textValue;
	private Date dateTime;
	
	//API constructor
	public PhenoInput (int patientNumber, String concept, String numericVal, String textVal, Date timestamp) {
		patientNum = patientNumber;
		conceptPath = concept;
		numericValue = numericVal;
		textValue = textVal;
		dateTime = timestamp;
	}
	
	// alternative API constructor -- use setter to adjust afterwards
	public PhenoInput () {
		patientNum = 0;
		conceptPath = null;
		numericValue = null;
		textValue = null;
		dateTime = new Date(0);
	}
	
	public int getPatientNum() {
		return patientNum;
	}
	public void setPatientNum(int patientNum) {
		this.patientNum = patientNum;
	}
	public String getConceptPath() {
		return conceptPath;
	}
	public void setConceptPath(String conceptPath) {
		this.conceptPath = conceptPath;
	}
	public String getNumericValue() {
		return numericValue;
	}
	public void setNumericValue(String numericValue) {
		this.numericValue = numericValue;
	}
	public String getTextValue() {
		return textValue;
	}
	public void setTextValue(String textValue) {
		this.textValue = textValue;
	}
	public Date getDateTime() {
		return dateTime;
	}
	public void setDateTime(Date dateTime) {
		this.dateTime = dateTime;
	}

	public String sanitizeConceptPath() {
		String cp = getConceptPath();
		String[] segments = cp.split("\\\\");
		for(int x = 0; x<segments.length; x++) {
			segments[x] = segments[x].trim();
		}
		String trimmedConceptPath = String.join("\\", segments) + "\\";
		String sanitizedConceptPath = trimmedConceptPath.replaceAll("\\ufffd", "");
		return sanitizedConceptPath;
	}

	public String sanitizeTextValue() {
		String sanitizedTextValue = null;
		String tv = getTextValue();
		if(tv != null) {
			sanitizedTextValue = tv.replaceAll("\\ufffd", "");
		}
		return sanitizedTextValue;
	}

	public String sanitizeNumericValue() {
		// This is not getDouble because we need to handle null values, not coerce them into 0s
		String numericValue = getNumericValue();
		if(numericValue == null || numericValue.isEmpty()) {
			String tv = sanitizeTextValue();
			if (tv != null) {
				try {
					numericValue = Double.parseDouble(tv) + "";
				}catch(NumberFormatException e) {
				
				}
			}
		}
		return numericValue;
	}

	public boolean isAlpha() {
		String nv = sanitizeNumericValue();
		boolean isAlphaNumeric = (nv == null || nv.isEmpty());
		return isAlphaNumeric;
	}
}
