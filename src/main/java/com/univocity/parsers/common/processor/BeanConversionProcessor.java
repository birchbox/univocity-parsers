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
package com.univocity.parsers.common.processor;

import java.beans.*;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

import com.univocity.parsers.annotations.*;
import com.univocity.parsers.annotations.helpers.*;
import com.univocity.parsers.common.*;
import com.univocity.parsers.conversions.*;

/**
 *
 * The base class for {@link RowProcessor} and {@link RowWriterProcessor} implementations that support java beans annotated with the annotations provided in {@link com.univocity.parsers.annotations}.
 *
 * @see RowProcessor
 * @see RowWriterProcessor
 *
 * @param <T> the annotated class type.
 *
 * @author uniVocity Software Pty Ltd - <a href="mailto:parsers@univocity.com">parsers@univocity.com</a>
 *
 */
abstract class BeanConversionProcessor<T> extends ConversionProcessor {

    private final Class<T> beanClass;
    private final Set<FieldMapping> parsedFields = new HashSet<FieldMapping>();
    private int lastFieldIndexMapped = -1;
    private FieldMapping[] readOrder;
    private boolean initialized = false;

    /**
     * Initializes the BeanConversionProcessor with the annotated bean class
     * @param beanType the class annotated with one or more of the annotations provided in {@link com.univocity.parsers.annotations}.
     */
    public BeanConversionProcessor(Class<T> beanType) {
        this.beanClass = beanType;
    }

    /**
     * Identifies and extracts fields annotated with the {@link Parsed} annotation
     */
    protected final void initialize() {
        if (!initialized) {
            initialized = true;

            Map<String, PropertyDescriptor> properties = new HashMap<String, PropertyDescriptor>();

            try {
                BeanInfo beanInfo = Introspector.getBeanInfo(beanClass, Object.class);
                for (PropertyDescriptor property : beanInfo.getPropertyDescriptors()) {
                    String name = property.getName();
                    properties.put(name, property);
                }
            } catch (IntrospectionException e) {
                //ignore and proceed to get fields direcly
            }

            Set<String> used = new HashSet<String>();
            Class<?> clazz = beanClass;
            do {
                Field[] declared = clazz.getDeclaredFields();
                for (Field field : declared) {
                    if (used.contains(field.getName())) {
                        continue;
                    }
                    Parsed annotation = field.getAnnotation(Parsed.class);
                    if (annotation != null) {
                        FieldMapping mapping = new FieldMapping(beanClass, field, properties.get(field.getName()));
                        parsedFields.add(mapping);
                        setupConversions(field, mapping);
                        used.add(field.getName());
                    }
                }
                clazz = clazz.getSuperclass();
            } while (clazz != null && clazz != Object.class);

            readOrder = null;
            lastFieldIndexMapped = -1;
        }
    }

    /**
     * Goes through each field annotated with {@link Parsed} and extracts the sequence of {@link Conversion} elements associated with each one.
     * @param field the field annotated with {@link Parsed} that must be associated with one or more {@link Conversion} objects
     * @param mapping a helper class to store information how the field is mapped to a parsed record.
     */
    @SuppressWarnings("rawtypes")
    private void setupConversions(Field field, FieldMapping mapping) {
        Annotation[] annotations = field.getAnnotations();

        Conversion lastConversion = null;
        for (Annotation annotation : annotations) {
            try {
                Conversion conversion = AnnotationHelper.getConversion(field, annotation);
                if (conversion != null) {
                    addConversion(conversion, mapping);
                    lastConversion = conversion;

                }
            } catch (Throwable ex) {
                String path = annotation.annotationType().getSimpleName() + "' of field '" + field.getName() + "' in " + this.beanClass.getName();
                throw new IllegalArgumentException("Error processing annotation '" + path + ". " + ex.getMessage(), ex);
            }
        }

        if (field.getAnnotation(Parsed.class).applyDefaultConversion()) {
            Conversion defaultConversion = AnnotationHelper.getDefaultConversion(field);
            if (applyDefaultConversion(lastConversion, defaultConversion)) {
                addConversion(defaultConversion, mapping);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private boolean applyDefaultConversion(Conversion lastConversionApplied, Conversion defaultConversion) {
        if (defaultConversion == null) {
            return false;
        }
        if (lastConversionApplied == null) {
            return true;
        }

        if (lastConversionApplied.getClass() == defaultConversion.getClass()) {
            // no need to add the default conversion as it was manually specified by the user with his settings
            return false;
        }

        Method execute = getConversionMethod(lastConversionApplied, "execute");
        Method revert = getConversionMethod(lastConversionApplied, "revert");

        Method defaultExecute = getConversionMethod(defaultConversion, "execute");
        Method defaultRevert = getConversionMethod(defaultConversion, "revert");

        return !(execute.getReturnType() == defaultExecute.getReturnType() && revert.getReturnType() == defaultRevert.getReturnType());

    }

    @SuppressWarnings("rawtypes")
    private Method getConversionMethod(Conversion conversion, String methodName) {
        Method targetMethod = null;
        for (Method method : conversion.getClass().getMethods()) {
            if (method.getName().equals(methodName) && !method.isSynthetic() && !method.isBridge() && ((method.getModifiers() & Modifier.PUBLIC) == 1) && method.getParameterTypes().length == 1 && method.getReturnType() != Void.class) {
                if (targetMethod != null) {
                    throw new IllegalArgumentException("Unable to convert values for class '" + beanClass + "'. Multiple '" + methodName + "' methods defined in conversion " + conversion.getClass() + ".");
                }
                targetMethod = method;
            }
        }
        if (targetMethod != null) {
            return targetMethod;
        }
        //should never happen
        throw new IllegalArgumentException("Unable to convert values for class '" + beanClass + "'. Cannot find method '" + methodName + "' in conversion " + conversion.getClass() + ".");
    }

    /**
     * Associates a conversion to a field of the java bean class.
     * @param conversion The conversion object that must be executed against the given field
     * @param mapping the helper object that contains informatin about how a field is mapped.
     */
    @SuppressWarnings("rawtypes")
    private void addConversion(Conversion conversion, FieldMapping mapping) {
        if (conversion == null) {
            return;
        }

        if (mapping.isMappedToIndex()) {
            this.convertIndexes(conversion).add(mapping.getIndex());
        } else {
            this.convertFields(conversion).add(mapping.getFieldName());
        }
    }

    /**
     * Goes through a list of objects and associates each value to a particular field of a java bean instance
     * @param instance the java bean instance that is going to have its properties set
     * @param row the values to associate with each field of the javabean.
     * @param context information about the current parsing process.
     */
    private void mapValuesToFields(T instance, Object[] row, ParsingContext context) {
        if (row.length > lastFieldIndexMapped) {
            this.lastFieldIndexMapped = row.length;
            mapFieldIndexes(context, row, context.headers(), context.extractedFieldIndexes(), context.columnsReordered());
        }

        int last = row.length < readOrder.length ? row.length : readOrder.length;
        for (int i = 0; i < last; i++) {
            FieldMapping field = readOrder[i];
            if (field != null) {
                Object value = row[i];
                field.write(instance, value);
            }
        }
    }

    /**
     * Identifies which fields are associated with which columns in a row.
     *
     * @param row A row with values for the given java bean.
     * @param headers The names of all fields of the record (including any header that is not mapped to the java bean). May be null if no headers have been defined in {@link CommonSettings#getHeaders()}
     * @param indexes The indexes of the headers or row that are actually being used. May be null if no fields have been selected using {@link CommonSettings#selectFields(String...)} or {@link CommonSettings#selectIndexes(Integer...)}
     * @param columnsReordered Indicates the indexes provided were reordered and do not match the original sequence of headers.
     */

    private void mapFieldIndexes(ParsingContext context, Object[] row, String[] headers, int[] indexes, boolean columnsReordered) {
        if (headers == null) {
            headers = ArgumentUtils.EMPTY_STRING_ARRAY;
        }
        int biggestIndex = headers.length > row.length ? headers.length : row.length;
        for (FieldMapping mapping : parsedFields) {
            int index = mapping.getIndex();
            if (biggestIndex < index) {
                biggestIndex = index;
            }
        }

        FieldMapping[] fieldOrder = new FieldMapping[biggestIndex];

        for (FieldMapping mapping : parsedFields) {
            if (mapping.isMappedToField()) {
                int index = ArgumentUtils.indexOf(headers, mapping.getFieldName());
                if (index == -1) {
                    if (headers.length == 0) {
                        throw new IllegalArgumentException("Could not find field with name '" + mapping.getFieldName() + "' in input. Please enable header extraction in the parser settings in order to match field names.");
                    }
                    throw new IllegalArgumentException("Could not find field with name '" + mapping.getFieldName() + "' in input. Names found: " + Arrays.toString(headers));
                }
                fieldOrder[index] = mapping;
            } else {
                if (mapping.getIndex() < fieldOrder.length) {
                    fieldOrder[mapping.getIndex()] = mapping;
                }
            }
        }

        if (indexes != null) {
            // sets fields not read from CSV to null.
            for (int i = 0; i < fieldOrder.length; i++) {
                boolean isIndexUsed = false;
                for (int j = 0; j < indexes.length; j++) {
                    if (indexes[j] == i) {
                        isIndexUsed = true;
                        break;
                    }
                }
                if (!isIndexUsed) {
                    fieldOrder[i] = null;
                }
            }

            // reorders the fields so they are positioned in the same order as in the incoming row[]
            if (columnsReordered) {
                FieldMapping[] newFieldOrder = new FieldMapping[indexes.length];

                for (int i = 0; i < indexes.length; i++) {
                    for (int j = 0; j < fieldOrder.length; j++) {
                        int index = indexes[i];
                        FieldMapping field = fieldOrder[index];
                        newFieldOrder[i] = field;
                    }
                }

                fieldOrder = newFieldOrder;
            }
        }

        this.readOrder = fieldOrder;

    }

    /**
     * Converts a record with values extracted from the parser into a java bean instance.
     * @param row The values extracted from the parser
     * @param context The current state of the parsing process
     * @return an instance of the java bean type defined in this class constructor.
     */
    public final T createBean(String[] row, ParsingContext context) {
        Object[] convertedRow = super.applyConversions(row, context);

        T instance;
        try {
            instance = beanClass.newInstance();
        } catch (Throwable e) {
            throw new IllegalArgumentException("Unable to instantiate class '" + beanClass.getName() + "'", e);
        }
        mapValuesToFields(instance, convertedRow, context);
        return instance;
    }

    /**
     * Iterates over all fields in the java bean instance and extracts its values.
     * @param instance the java bean instance to be read
     * @param row object array that will receive the values extracted from java bean
     * @param headers The names of all fields of the record (including any header that is not mapped to the java bean). May be null if no headers have been defined in {@link CommonSettings#getHeaders()}
     * @param indexes The indexes of the headers or row that are actually being used. May be null if no fields have been selected using {@link CommonSettings#selectFields(String...)} or {@link CommonSettings#selectIndexes(Integer...)}
     * @param columnsReordered Indicates the indexes provided were reordered and do not match the original sequence of headers.
     */
    private void mapFieldsToValues(T instance, Object[] row, String[] headers, int[] indexes, boolean columnsReordered) {
        if (row.length > this.lastFieldIndexMapped) {
            mapFieldIndexes(null, row, headers, indexes, columnsReordered);
        }

        int last = row.length < readOrder.length ? row.length : readOrder.length;
        for (int i = 0; i < last; i++) {
            FieldMapping field = readOrder[i];
            if (field != null) {
                row[i] = field.read(instance);
            }
        }
    }

    /**
     * Converts a java bean instance into a sequence of values for writing.
     *
     * @param bean an instance of the type defined in this class constructor.
     * @param headers All field names used to produce records in a given destination. May be null if no headers have been defined in {@link CommonSettings#getHeaders()}
     * @param indexesToWrite The indexes of the headers that are actually being written. May be null if no fields have been selected using {@link CommonSettings#selectFields(String...)} or {@link CommonSettings#selectIndexes(Integer...)}
     * @return a row of objects containing the values extracted from the java bean
     */
    public final Object[] reverseConversions(T bean, String[] headers, int[] indexesToWrite) {
        Object[] row;
        if (indexesToWrite != null) {
            row = new Object[indexesToWrite.length];
        } else {
            row = new Object[headers.length];
        }

        if (bean != null) {
            mapFieldsToValues(bean, row, headers, indexesToWrite, false);
        }

        super.reverseConversions(true, row, headers, indexesToWrite);

        return row;
    }

    /**
     * Returns the class of the annotated java bean instances that will be manipulated by this processor.
     * @return the class of the annotated java bean instances that will be manipulated by this processor.
     */
    public Class<T> getBeanClass() {
        return beanClass;
    }
}
