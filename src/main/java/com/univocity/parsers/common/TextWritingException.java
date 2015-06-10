/*******************************************************************************
 * Copyright 2014 uniVocity Software Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.univocity.parsers.common;

import java.util.*;

/**
 * Exception type used provide information about any issue that might happen while writing to a given output.
 *
 *  <p> It generally provides location and data information in case of a writing failure.
 *
 * @author uniVocity Software Pty Ltd - <a href="mailto:parsers@univocity.com">parsers@univocity.com</a>
 *
 */
public class TextWritingException extends RuntimeException {

	private static final long serialVersionUID = 7198462597717255519L;

	private final long recordCount;
	private final Object[] recordData;
	private final String recordCharacters;

	private TextWritingException(String message, long recordCount, Object[] row, String recordCharacters, Throwable cause) {
		super(message, cause);
		this.recordCount = recordCount;
		this.recordData = row;
		this.recordCharacters = recordCharacters;
	}

	public TextWritingException(String message, long recordCount, String recordCharacters, Throwable cause) {
		this(message, recordCount, null, recordCharacters, cause);
	}

	public TextWritingException(String message, long recordCount, Object[] row, Throwable cause) {
		this(message, recordCount, row, null, cause);
	}

	public TextWritingException(String message) {
		this(message, 0, null, null, null);
	}

	public TextWritingException(Throwable cause) {
		this(cause != null ? cause.getMessage() : null, 0, null, null, cause);
	}

	public TextWritingException(String message, long line, Object[] row) {
		this(message, line, row, null);
	}

	public TextWritingException(String message, long line, String recordCharacters) {
		this(message, line, null, recordCharacters, null);
	}

	@Override
	public String getMessage() {
		String msg = super.getMessage();
		msg = msg == null ? "" : msg;

		if (recordData != null) {
			return "Error writing data: " + msg + ", recordCount=" + recordCount + ", recordData=" + Arrays.toString(recordData);
		}

		if (recordCharacters != null) {
			return "Error writing data: " + msg + ", recordCount=" + recordCount + ", recordData=[" + recordCharacters + "]";
		}

		return "Error writing data: " + msg + ", recordCount=" + recordCount + "]";
	}

	/**
	 * Returns the number of records written before the exception occurred.
	 * @return the number of records written before the exception occurred.
	 */
	public long getRecordCount() {
		return recordCount;
	}

	/**
	 * Returns the data that failed to be written
	 * @return the data that failed to be written
	 */
	public Object[] getRecordData() {
		return recordData;
	}

	/**
	 * Returns the character data that failed to be written
	 * @return the character data that failed to be written
	 */
	public String getRecordCharacters() {
		return recordCharacters;
	}
}
