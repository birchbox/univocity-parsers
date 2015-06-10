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
package com.univocity.parsers.csv;

import java.io.*;

import com.univocity.parsers.common.*;

/**
 * A powerful and flexible CSV writer implementation.
 *
 * @see CsvFormat
 * @see CsvWriterSettings
 * @see CsvParser
 * @see AbstractWriter
 *
 * @author uniVocity Software Pty Ltd - <a href="mailto:parsers@univocity.com">parsers@univocity.com</a>
 *
 */
public class CsvWriter extends AbstractWriter<CsvWriterSettings> {

	private final char separator;
	private final char quotechar;
	private final char escapechar;
	private final boolean ignoreLeading;
	private final boolean ignoreTrailing;
	private final boolean quoteAllFields;
	private final char newLine;

	/**
	 * The CsvWriter supports all settings provided by {@link CsvWriterSettings}, and requires this configuration to be properly initialized.
	 * @param writer the output resource that will receive CSV records produced by this class.
	 * @param settings the CSV writer configuration
	 */
	public CsvWriter(Writer writer, CsvWriterSettings settings) {
		super(writer, settings);

		CsvFormat format = settings.getFormat();
		this.separator = format.getDelimiter();
		this.quotechar = format.getQuote();
		this.escapechar = format.getQuoteEscape();
		this.newLine = format.getNormalizedNewline();

		this.quoteAllFields = settings.getQuoteAllFields();
		this.ignoreLeading = settings.getIgnoreLeadingWhitespaces();
		this.ignoreTrailing = settings.getIgnoreTrailingWhitespaces();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void processRow(Object[] row) {
		for (int i = 0; i < row.length; i++) {
			if (i != 0) {
				appendToRow(separator);
			}

			String nextElement = getStringValue(row[i]);
			boolean isElementQuoted = quoteElement(nextElement);

			if (isElementQuoted) {
				appender.append(quotechar);
			}

			int originalLength = appender.length();
			append(isElementQuoted, nextElement);

			//skipped all whitespaces and wrote nothing
			if (appender.length() == originalLength && nullValue != null && !nullValue.isEmpty()) {
				if (isElementQuoted) {
					append(true, emptyValue);
				} else {
					append(false, nullValue);
				}
			}

			if (isElementQuoted) {
				appendValueToRow();
				appendToRow(quotechar);
			} else {
				appendValueToRow();
			}
		}
	}

	private boolean quoteElement(String nextElement) {
		if (quoteAllFields) {
			return true;
		}
		if (nextElement == null) {
			return false;
		}

		for (int j = 0; j < nextElement.length(); j++) {
			char nextChar = nextElement.charAt(j);
			if (nextChar == separator || nextChar == newLine) {
				return true;
			}
		}
		return false;
	}

	private void append(boolean isElementQuoted, String element) {
		if (element == null) {
			element = nullValue;
		}
		if (element == null) {
			return;
		}

		int start = 0;
		if (this.ignoreLeading) {
			start = skipLeadingWhitespace(element);
		}

		if (this.ignoreTrailing) {
			for (int i = start; i < element.length(); i++) {
				char nextChar = element.charAt(i);
				if (isElementQuoted && nextChar == quotechar) {
					appender.appendIgnoringWhitespace(escapechar);
				}
				appender.appendIgnoringWhitespace(nextChar);
			}
		} else {
			for (int i = start; i < element.length(); i++) {
				char nextChar = element.charAt(i);
				if (isElementQuoted && nextChar == quotechar) {
					appender.append(escapechar);
				}
				appender.append(nextChar);
			}
		}
	}
}
