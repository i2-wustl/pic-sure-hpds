package edu.harvard.hms.dbmi.avillach.hpds.processing;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

import org.apache.log4j.Logger;

import edu.harvard.hms.dbmi.avillach.hpds.data.phenotype.ColumnMeta;
import edu.harvard.hms.dbmi.avillach.hpds.exception.NotEnoughMemoryException;
import edu.harvard.hms.dbmi.avillach.hpds.exception.ResultSetTooLargeException;

public class ResultStore {

	private static Logger log = Logger.getLogger(ResultStore.class);

	private List<ColumnMeta> columns;
	int rowWidth;
	private int numRows;
	
	private static final ColumnMeta PATIENT_ID_COLUMN_META = new ColumnMeta().setColumnOffset(0).setName("PatientId").setWidthInBytes(Integer.BYTES);
	byte[] resultArray;
	
	public ResultStore(String resultId, String queryId, List<ColumnMeta> columns, TreeSet<Integer> ids) throws NotEnoughMemoryException {
		this.columns = new ArrayList<ColumnMeta>();
		this.numRows = ids.size() + 1;
		this.getColumns().add(PATIENT_ID_COLUMN_META);
		int rowWidth = Integer.BYTES;
		for(ColumnMeta column : columns) {
			column.setColumnOffset(rowWidth);
			rowWidth += column.getWidthInBytes();
			this.getColumns().add(column);
		}
		this.rowWidth = rowWidth;
		if(1L * this.rowWidth * this.getNumRows() > Integer.MAX_VALUE) {
			throw new ResultSetTooLargeException((1L * this.rowWidth * this.getNumRows())/(Integer.MAX_VALUE/2));
		}
		try {
			log.info("Allocating result array : " + resultId);
			resultArray = new byte[this.rowWidth * this.getNumRows()];
		} catch(Error e) {
			throw new NotEnoughMemoryException();			
		}
		log.info("Store created for " + this.getNumRows() + " rows and " + columns.size() + " columns ");
		int x = 0;
		for(Integer id : ids) {
			writeField(0,x++,ByteBuffer.allocate(Integer.BYTES).putInt(id).array());
		}
	}

	public void writeField(int column, int row, byte[] fieldValue) {
		int offset = getFieldOffset(row,column);
		try {
			System.arraycopy(fieldValue, 0, resultArray, offset, fieldValue.length);
		}catch(Exception e) {
			log.info("Exception caught writing field : " + column + ", " + row + " : " + fieldValue.length + " bytes into result at offset " + offset + " of " + resultArray.length);
			throw e;
		}
	}

	public int getFieldOffset(int row, int column) {
		int rowOffset = row*rowWidth;
		int columnOffset = getColumns().get(column).getColumnOffset();
		return rowOffset + columnOffset;
	}

	ByteBuffer wrappedResultArray;
	public void readRowIntoStringArray(int rowNumber, String[] row) throws IOException {
		if(wrappedResultArray == null) {
			wrappedResultArray = ByteBuffer.wrap(resultArray);
		}
		row[0] = wrappedResultArray.getInt(getFieldOffset(rowNumber, 0)) + "";

		byte[][] columnBuffers = new byte[getColumns().size()][];
		for(int x = 0;x<getColumns().size();x++) {
			columnBuffers[x] = new byte[getColumns().get(x).getWidthInBytes()];
		}

		stringifyRow(rowNumber, row, columnBuffers);
	}

	private void stringifyRow(int rowNumber, String[] row, byte[][] columnBuffers) {
		for(int x = 1;x<row.length;x++) {
			ColumnMeta columnMeta = getColumns().get(x);
			int fieldOffset = getFieldOffset(rowNumber, x);
			if(columnMeta.isCategorical()) {
				stringifyString(row, columnBuffers, x, fieldOffset);
			} else {
				stringifyDouble(row, x, fieldOffset);
			}
		}
	}

	DecimalFormat decimalFormat = new DecimalFormat("######.##");
	private void stringifyDouble(String[] row, int x, int fieldOffset) {
		row[x] = Double.valueOf(wrappedResultArray.getDouble(fieldOffset)).toString();
	}

	private void stringifyString(String[] row, byte[][] columnBuffers, int x, int fieldOffset) {
		row[x] = new String(Arrays.copyOfRange(resultArray, fieldOffset, fieldOffset + columnBuffers[x].length)).trim();
	}

	public int getNumRows() {
		return numRows;
	}

	public List<ColumnMeta> getColumns() {
		return columns;
	}
}
