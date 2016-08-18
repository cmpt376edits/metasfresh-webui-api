package de.metas.ui.web.window.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.adempiere.ad.callout.api.ICalloutField;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.util.DisplayType;
import org.slf4j.Logger;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import de.metas.logging.LogManager;
import de.metas.ui.web.window.datatypes.LookupValue;
import de.metas.ui.web.window.datatypes.LookupValue.IntegerLookupValue;
import de.metas.ui.web.window.datatypes.LookupValue.StringLookupValue;
import de.metas.ui.web.window.datatypes.json.JSONDate;
import de.metas.ui.web.window.datatypes.json.JSONLookupValue;
import de.metas.ui.web.window.datatypes.json.JSONValues;
import de.metas.ui.web.window.descriptor.DocumentFieldDescriptor;
import de.metas.ui.web.window.exceptions.DocumentFieldNotLookupException;

/*
 * #%L
 * metasfresh-webui-api
 * %%
 * Copyright (C) 2016 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

/*package*/class DocumentField implements IDocumentFieldView
{
	private static final Logger logger = LogManager.getLogger(DocumentField.class);

	private final DocumentFieldDescriptor descriptor;
	private final Document _document;
	private final LookupDataSource lookupDataSource;

	private transient ICalloutField _calloutField; // lazy

	//
	// State
	private Object _initialValue;
	private Object _value;
	private boolean _mandatory = false;
	private boolean _readonly = false;
	private boolean _displayed = false;
	private boolean _valid = false;

	/* package */ DocumentField(final DocumentFieldDescriptor descriptor, final Document document)
	{
		super();
		this.descriptor = descriptor;
		_document = document;
		lookupDataSource = descriptor.getDataBinding().createLookupDataSource();
		_valid = false;
	}

	/** copy constructor */
	private DocumentField(DocumentField from, Document document)
	{
		super();
		this.descriptor = from.descriptor;
		_document = document;
		lookupDataSource = from.lookupDataSource == null ? null : from.lookupDataSource.copy();
		_calloutField = null; // don't copy it
		_initialValue = from._initialValue;
		_value = from._value;
		_mandatory = from._mandatory;
		_readonly = from._readonly;
		_displayed = from._displayed;
		_valid = from._valid;
	}

	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this)
				.add("fieldName", getFieldName())
				.add("value", _value)
				.add("initalValue", _initialValue)
				.add("mandatory", _mandatory)
				.add("readonly", _readonly)
				.add("displayed", _displayed)
				.toString();
	}

	@Override
	public DocumentFieldDescriptor getDescriptor()
	{
		return descriptor;
	}

	/* package */Document getDocument()
	{
		return _document;
	}

	@Override
	public String getFieldName()
	{
		return descriptor.getFieldName();
	}

	@Override
	public boolean isKey()
	{
		return descriptor.isKey();
	}

	@Override
	public boolean isVirtualField()
	{
		return descriptor.isVirtualField();
	}

	@Override
	public boolean isCalculated()
	{
		return descriptor.isCalculated();
	}

	@Override
	public Object getInitialValue()
	{
		return _initialValue;
	}

	/**
	 * @param value
	 */
	/* package */ void setInitialValue(final Object value)
	{
		final Object valueConv = convertToValueClass(value);
		_initialValue = valueConv;
		_value = valueConv;
		if (logger.isTraceEnabled())
		{
			logger.trace("Set {}'s initial value: {}", getFieldName(), valueConv);
		}

		updateValid();
	}

	/* package */void setValue(final Object value)
	{
		final Object valueNew = convertToValueClass(value);
		final Object valueOld = _value;

		if (Objects.equal(valueNew, valueOld))
		{
			return;
		}

		_value = valueNew;
		if (logger.isTraceEnabled())
		{
			logger.trace("Changed {}'s value: {} -> {}", getFieldName(), valueOld, valueNew);
		}

		updateValid();
	}

	@Override
	public Object getValue()
	{
		return _value;
	}

	@Override
	public Object getValueAsJsonObject()
	{
		return JSONValues.valueToJsonObject(_value);
	}

	@Override
	public int getValueAsInt(final int defaultValue)
	{
		final Integer valueInt = convertToValueClass(_value, Integer.class);
		return valueInt == null ? defaultValue : valueInt;
	}

	@Override
	public boolean getValueAsBoolean()
	{
		final Boolean valueBoolean = convertToValueClass(_value, Boolean.class);
		return valueBoolean != null && valueBoolean.booleanValue();
	}

	@Override
	public Object getOldValue()
	{
		// TODO to implement. "getOldValue" is mainly needed for ICalloutField and DocumentInterfaceWrapper
		return getValue();
	}

	private Object convertToValueClass(final Object value)
	{
		final Class<?> targetType = descriptor.getValueClass();
		return convertToValueClass(value, targetType);
	}

	private <T> T convertToValueClass(final Object value, final Class<T> targetType)
	{
		if (value == null)
		{
			return null;
		}

		final Class<?> fromType = value.getClass();

		try
		{
			if (targetType.isAssignableFrom(fromType))
			{
				@SuppressWarnings("unchecked")
				final T valueConv = (T)value;
				return valueConv;
			}

			if (String.class == targetType)
			{
				if (Map.class.isAssignableFrom(fromType))
				{
					// this is not allowed for consistency. let it fail.
				}
				// For any other case, blindly convert it to string
				else
				{
					@SuppressWarnings("unchecked")
					final T valueConv = (T)value.toString();
					return valueConv;
				}
			}
			else if (java.util.Date.class == targetType)
			{
				if (value instanceof String)
				{
					@SuppressWarnings("unchecked")
					final T valueConv = (T)JSONDate.fromJson((String)value);
					return valueConv;
				}
			}
			else if (Integer.class == targetType)
			{
				if (value instanceof String)
				{
					@SuppressWarnings("unchecked")
					final T valueConv = (T)(Integer)Integer.parseInt((String)value);
					return valueConv;
				}
				else if (value instanceof Number)
				{
					@SuppressWarnings("unchecked")
					final T valueConv = (T)(Integer)((Number)value).intValue();
					return valueConv;
				}
			}
			else if (BigDecimal.class == targetType)
			{
				if (String.class == fromType)
				{
					@SuppressWarnings("unchecked")
					final T valueConv = (T)new BigDecimal((String)value);
					return valueConv;
				}
			}
			else if (Boolean.class == targetType)
			{
				@SuppressWarnings("unchecked")
				final T valueConv = (T)DisplayType.toBoolean(value, Boolean.FALSE);
				return valueConv;
			}
			else if (IntegerLookupValue.class == targetType)
			{
				if (Map.class.isAssignableFrom(fromType))
				{
					@SuppressWarnings("unchecked")
					final Map<String, String> map = (Map<String, String>)value;
					@SuppressWarnings("unchecked")
					final T valueConv = (T)JSONLookupValue.integerLookupValueFromJsonMap(map);
					return valueConv;
				}
				else if (Integer.class.isAssignableFrom(fromType))
				{
					final Integer valueInt = (Integer)value;
					if (lookupDataSource != null)
					{
						@SuppressWarnings("unchecked")
						final T valueConv = (T)lookupDataSource.findById(valueInt);
						// TODO: what if valueConv was not found?
						return valueConv;
					}
				}
				else if (String.class == fromType)
				{
					final String valueStr = (String)value;
					if (valueStr.isEmpty())
					{
						return null;
					}

					if (lookupDataSource != null)
					{
						@SuppressWarnings("unchecked")
						final T valueConv = (T)lookupDataSource.findById(valueStr);
						// TODO: what if valueConv was not found?
						return valueConv;
					}
				}
			}
			else if (StringLookupValue.class == targetType)
			{
				if (Map.class.isAssignableFrom(fromType))
				{
					@SuppressWarnings("unchecked")
					final Map<String, String> map = (Map<String, String>)value;
					@SuppressWarnings("unchecked")
					final T valueConv = (T)JSONLookupValue.stringLookupValueFromJsonMap(map);
					return valueConv;
				}
				else if (String.class == fromType)
				{
					final String valueStr = (String)value;
					if (valueStr.isEmpty())
					{
						return null;
					}

					if (lookupDataSource != null)
					{
						@SuppressWarnings("unchecked")
						final T valueConv = (T)lookupDataSource.findById(valueStr);
						// TODO: what if valueConv was not found?
						return valueConv;
					}
				}
			}
		}
		catch (final Exception e)
		{
			throw new AdempiereException("Cannot convert " + getFieldName() + "'s value '" + value + "' (" + fromType + ") to " + targetType, e);
		}

		throw new AdempiereException("Cannot convert " + getFieldName() + "'s value '" + value + "' (" + fromType + ") to " + targetType);
	}

	@Override
	public boolean isMandatory()
	{
		return _mandatory;
	}

	/* package */ void setMandatory(final boolean mandatory)
	{
		if (_mandatory == mandatory)
		{
			return;
		}

		_mandatory = mandatory;
		updateValid();
	}

	@Override
	public boolean isReadonly()
	{
		return _readonly;
	}

	/* package */void setReadonly(final boolean readonly)
	{
		_readonly = readonly;
	}

	@Override
	public boolean isDisplayed()
	{
		return _displayed;
	}

	/* package */void setDisplayed(final boolean displayed)
	{
		_displayed = displayed;
	}

	@Override
	public boolean isLookupValuesStale()
	{
		return lookupDataSource != null && lookupDataSource.isStaled();
	}

	/* package */ boolean setLookupValuesStaled(final String triggeringFieldName)
	{
		if (lookupDataSource == null)
		{
			return false;
		}
		return lookupDataSource.setStaled(triggeringFieldName);
	}

	public boolean isLookupWithNumericKey()
	{
		return lookupDataSource != null && lookupDataSource.isNumericKey();
	}

	/* package */List<LookupValue> getLookupValues(final Document document)
	{
		if(lookupDataSource == null)
		{
			throw new DocumentFieldNotLookupException(getFieldName());
		}
		return lookupDataSource.findEntities(document, LookupDataSource.DEFAULT_PageLength);
	}

	/* package */List<LookupValue> getLookupValuesForQuery(final Document document, final String query)
	{
		if(lookupDataSource == null)
		{
			throw new DocumentFieldNotLookupException(getFieldName());
		}
		return lookupDataSource.findEntities(document, query, LookupDataSource.FIRST_ROW, LookupDataSource.DEFAULT_PageLength);
	}

	public ICalloutField asCalloutField()
	{
		if (_calloutField == null)
		{
			_calloutField = new DocumentFieldAsCalloutField(this);
		}
		return _calloutField;
	}

	public final void updateValid()
	{
		final boolean validOld = _valid;
		final boolean validNew = checkValid();
		if (validOld == validNew)
		{
			return;
		}

		_valid = validNew;
		logger.debug("Changed valid state {}->{} for {}", validOld, validNew, this);
	}

	private final boolean checkValid()
	{
		if (isMandatory() && getValue() == null)
		{
			logger.debug("Not valid because mandatory field is not filled: {}", this);
			return false;
		}

		return true;
	}

	/* package */boolean isValid()
	{
		return _valid;
	}

	public boolean hasChanges()
	{
		return Objects.equal(_value, _initialValue);
	}

	/*package */DocumentField copy(final Document document)
	{
		return new DocumentField(this, document);
	}
}