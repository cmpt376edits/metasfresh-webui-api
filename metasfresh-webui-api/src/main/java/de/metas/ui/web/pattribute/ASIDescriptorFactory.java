package de.metas.ui.web.pattribute;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Function;

import org.adempiere.ad.expression.api.ConstantLogicExpression;
import org.adempiere.ad.expression.api.IExpression;
import org.adempiere.ad.expression.api.ILogicExpression;
import org.adempiere.model.InterfaceWrapperHelper;
import org.compiere.model.I_M_Attribute;
import org.compiere.model.I_M_AttributeInstance;
import org.compiere.model.I_M_AttributeSet;
import org.compiere.model.I_M_AttributeSetInstance;
import org.compiere.model.MAttribute;
import org.compiere.model.MAttributeSet;
import org.compiere.model.X_M_Attribute;
import org.compiere.util.CCache;
import org.compiere.util.Env;
import org.compiere.util.TimeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.hazelcast.util.function.BiConsumer;

import de.metas.printing.esb.base.util.Check;
import de.metas.ui.web.pattribute.ASILookupDescriptor.AttributeValue;
import de.metas.ui.web.window.datatypes.DocumentType;
import de.metas.ui.web.window.datatypes.LookupValue.StringLookupValue;
import de.metas.ui.web.window.descriptor.DocumentEntityDataBindingDescriptor;
import de.metas.ui.web.window.descriptor.DocumentEntityDataBindingDescriptor.DocumentEntityDataBindingDescriptorBuilder;
import de.metas.ui.web.window.descriptor.DocumentEntityDescriptor;
import de.metas.ui.web.window.descriptor.DocumentFieldDataBindingDescriptor;
import de.metas.ui.web.window.descriptor.DocumentFieldDescriptor;
import de.metas.ui.web.window.descriptor.DocumentFieldDescriptor.Characteristic;
import de.metas.ui.web.window.descriptor.DocumentFieldWidgetType;
import de.metas.ui.web.window.descriptor.DocumentLayoutElementDescriptor;
import de.metas.ui.web.window.descriptor.DocumentLayoutElementFieldDescriptor;
import de.metas.ui.web.window.descriptor.LookupDescriptor;
import de.metas.ui.web.window.descriptor.LookupDescriptor.LookupScope;
import de.metas.ui.web.window.model.DocumentsRepository;
import de.metas.ui.web.window.model.IDocumentFieldView;

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

@Component
public class ASIDescriptorFactory
{
	@Autowired
	private ASIRepository asiRepository;

	private final CCache<Integer, ASIDescriptor> cacheByAttributeSetId = CCache.newLRUCache(I_M_AttributeSet.Table_Name + "#Descriptors#by#M_AttributeSet_ID", 200, 0);

	private ASIDataBindingDescriptorBuilder _asiBindingsBuilder;

	private ASIDescriptorFactory()
	{
		super();
	}

	public ASIDataBindingDescriptorBuilder getAsiBindingsBuilder()
	{
		if (_asiBindingsBuilder == null)
		{
			_asiBindingsBuilder = new ASIDataBindingDescriptorBuilder(asiRepository);
		}
		return _asiBindingsBuilder;
	}

	public ASIDescriptor getProductAttributesDescriptor(final int attributeSetId)
	{
		return cacheByAttributeSetId.getOrLoad(attributeSetId, () -> retrieveProductAttributesDescriptor(attributeSetId));
	}

	private ASIDescriptor retrieveProductAttributesDescriptor(final int attributeSetId)
	{
		Check.assume(attributeSetId > 0, "attributeSetId > 0");

		final MAttributeSet attributeSet = MAttributeSet.get(Env.getCtx(), attributeSetId);
		final DocumentEntityDescriptor attributeSetDescriptor = createASIDescriptor(attributeSet);

		final ASILayout layout = createLayout(attributeSetDescriptor);

		return ASIDescriptor.builder()
				.setEntityDescriptor(attributeSetDescriptor)
				.setLayout(layout)
				.build();
	}

	private final DocumentEntityDescriptor createASIDescriptor(final MAttributeSet attributeSet)
	{
		final DocumentEntityDescriptor.Builder attributeSetDescriptor = DocumentEntityDescriptor.builder()
				.setDocumentType(DocumentType.ProductAttributes, attributeSet.getM_AttributeSet_ID())
				.setCaption(attributeSet.getName())
				.setDescription(attributeSet.getDescription())
				.setDataBinding(getAsiBindingsBuilder())
				.disableCallouts()
				// Defaults:
				.setDetailId(null)
				.setAD_Tab_ID(0)
				.setTableName(I_M_AttributeSetInstance.Table_Name)
				.setIsSOTrx(true)
				//
				;

		final MAttribute[] attributes = attributeSet.getMAttributes(true);
		for (final I_M_Attribute attribute : attributes)
		{
			final DocumentFieldDescriptor.Builder fieldDescriptor = createASIAttributeDescriptor(attribute);
			attributeSetDescriptor.addField(fieldDescriptor);
		}

		return attributeSetDescriptor.build();
	}

	private static DocumentFieldDescriptor.Builder createASIAttributeDescriptor(final I_M_Attribute attribute)
	{
		final int attributeId = attribute.getM_Attribute_ID();
		final String fieldName = attribute.getValue();
		final String attributeValueType = attribute.getAttributeValueType();

		final Class<?> valueClass;
		final DocumentFieldWidgetType widgetType;
		final Function<I_M_AttributeInstance, Object> readMethod;
		final BiConsumer<I_M_AttributeInstance, IDocumentFieldView> writeMethod;
		Function<LookupScope, LookupDescriptor> lookupDescriptorProvider = (scope) -> null;

		if (X_M_Attribute.ATTRIBUTEVALUETYPE_Date.equals(attributeValueType))
		{
			valueClass = java.util.Date.class;
			widgetType = DocumentFieldWidgetType.Date;
			readMethod = I_M_AttributeInstance::getValueDate;
			writeMethod = (aiRecord, field) -> aiRecord.setValueDate(TimeUtil.asTimestamp(field.getValueAs(java.util.Date.class)));
		}
		else if (X_M_Attribute.ATTRIBUTEVALUETYPE_List.equals(attributeValueType))
		{
			valueClass = StringLookupValue.class;
			widgetType = DocumentFieldWidgetType.List;
			readMethod = I_M_AttributeInstance::getValue;
			writeMethod = ASIAttributeFieldBinding::writeValueFromLookup;

			final LookupDescriptor lookupDescriptor = createLookupDescriptor(attribute);
			lookupDescriptorProvider = (scope) -> lookupDescriptor;
		}
		else if (X_M_Attribute.ATTRIBUTEVALUETYPE_Number.equals(attributeValueType))
		{
			valueClass = BigDecimal.class;
			widgetType = DocumentFieldWidgetType.Number;
			readMethod = I_M_AttributeInstance::getValueNumber;
			writeMethod = (aiRecord, field) -> aiRecord.setValueNumber(field.getValueAs(BigDecimal.class));
		}
		else if (X_M_Attribute.ATTRIBUTEVALUETYPE_StringMax40.equals(attributeValueType))
		{
			valueClass = String.class;
			widgetType = DocumentFieldWidgetType.Text;
			readMethod = I_M_AttributeInstance::getValue;
			writeMethod = (aiRecord, field) -> aiRecord.setValue(field.getValueAs(String.class));
		}
		else
		{
			throw new IllegalArgumentException("@NotSupported@ @AttributeValueType@=" + attributeValueType + ", @M_Attribute_ID@=" + attribute);
		}

		final ILogicExpression readonlyLogic = ILogicExpression.FALSE;
		final ILogicExpression displayLogic = ILogicExpression.TRUE;
		final ILogicExpression mandatoryLogic = ConstantLogicExpression.of(attribute.isMandatory());

		final Optional<IExpression<?>> defaultValueExpr = Optional.empty();

		return DocumentFieldDescriptor.builder(fieldName)
				.setCaption(attribute.getName())
				.setDescription(attribute.getDescription())
				//
				.setValueClass(valueClass)
				.setWidgetType(widgetType)
				.setLookupDescriptorProvider(lookupDescriptorProvider)
				//
				.setDefaultValueExpression(defaultValueExpr)
				.setReadonlyLogic(readonlyLogic)
				.setDisplayLogic(displayLogic)
				.setMandatoryLogic(mandatoryLogic)
				//
				.addCharacteristic(Characteristic.PublicField)
				//
				.setDataBinding(new ASIAttributeFieldBinding(attributeId, fieldName, attribute.isMandatory(), readMethod, writeMethod))
				//
				;
	}

	private static LookupDescriptor createLookupDescriptor(final I_M_Attribute attribute)
	{
		// TODO: caching
		return ASILookupDescriptor.of(attribute);
	}

	private static ASILayout createLayout(final DocumentEntityDescriptor attributeSetDescriptor)
	{
		final ASILayout.Builder layout = ASILayout.builder()
				.setCaption(attributeSetDescriptor.getCaption())
				.setM_AttributeSet_ID(attributeSetDescriptor.getDocumentTypeId());

		attributeSetDescriptor.getFields()
				.stream()
				.map(fieldDescriptor -> createLayoutElement(fieldDescriptor))
				.forEach(layoutElement -> layout.addElement(layoutElement));

		return layout.build();
	}

	private static DocumentLayoutElementDescriptor.Builder createLayoutElement(final DocumentFieldDescriptor fieldDescriptor)
	{
		return DocumentLayoutElementDescriptor.builder()
				.setCaption(fieldDescriptor.getCaption())
				.setWidgetType(fieldDescriptor.getWidgetType())
				.addField(DocumentLayoutElementFieldDescriptor.builder(fieldDescriptor.getFieldName())
						.setLookupSource(fieldDescriptor.getLookupSourceType())
						.setPublicField(true));
	}

	private static class ASIDataBindingDescriptorBuilder implements DocumentEntityDataBindingDescriptorBuilder
	{
		private final DocumentEntityDataBindingDescriptor dataBinding = new DocumentEntityDataBindingDescriptor()
		{
			@Override
			public DocumentsRepository getDocumentsRepository()
			{
				return asiRepository;
			}

		};

		private final ASIRepository asiRepository;

		private ASIDataBindingDescriptorBuilder(final ASIRepository asiRepository)
		{
			super();
			Check.assumeNotNull(asiRepository, "Parameter asiRepository is not null");
			this.asiRepository = asiRepository;
		}

		@Override
		public DocumentEntityDataBindingDescriptor getOrBuild()
		{
			return dataBinding;
		}
	}

	public static final class ASIAttributeFieldBinding implements DocumentFieldDataBindingDescriptor
	{
		private final int attributeId;
		private final String attributeName;
		private final boolean mandatory;
		private final Function<I_M_AttributeInstance, Object> readMethod;
		private final BiConsumer<I_M_AttributeInstance, IDocumentFieldView> writeMethod;

		private ASIAttributeFieldBinding( //
				final int attributeId, final String attributeName //
				, final boolean mandatory //
				, final Function<I_M_AttributeInstance, Object> readMethod //
				, final BiConsumer<I_M_AttributeInstance, IDocumentFieldView> writeMethod //
		)
		{
			super();

			Check.assume(attributeId > 0, "attributeId > 0");
			this.attributeId = attributeId;

			Check.assumeNotEmpty(attributeName, "attributeName is not empty");
			this.attributeName = attributeName;

			this.mandatory = mandatory;

			Check.assumeNotNull(readMethod, "Parameter readMethod is not null");
			this.readMethod = readMethod;

			Check.assumeNotNull(writeMethod, "Parameter writeMethod is not null");
			this.writeMethod = writeMethod;
		}

		@Override
		public String getColumnName()
		{
			return attributeName;
		}

		@Override
		public boolean isMandatory()
		{
			return mandatory;
		}

		public Object readValue(final I_M_AttributeInstance ai)
		{
			return readMethod.apply(ai);
		}

		private void writeValue(final I_M_AttributeInstance ai, final IDocumentFieldView field)
		{
			writeMethod.accept(ai, field);
		}

		private static final void writeValueFromLookup(final I_M_AttributeInstance ai, final IDocumentFieldView field)
		{
			final StringLookupValue lookupValue = field.getValueAs(StringLookupValue.class);
			final AttributeValue attributeValue = field.getDescriptor().getLookupDescriptor(LookupScope.DocumentField)
					.cast(ASILookupDescriptor.class)
					.getAttributeValue(lookupValue);

			if(attributeValue == null)
			{
				ai.setValue(lookupValue == null ? null : lookupValue.toString());
				ai.setM_AttributeValue(null);
			}
			else
			{
				ai.setValue(attributeValue.getValueAsString());
				ai.setM_AttributeValue_ID(attributeValue.getM_AttributeValue_ID());
			}
		}

		public void createAndSaveM_AttributeInstance(final I_M_AttributeSetInstance asiRecord, final IDocumentFieldView asiField)
		{
			final I_M_AttributeInstance aiRecord = InterfaceWrapperHelper.newInstance(I_M_AttributeInstance.class, asiRecord);
			aiRecord.setM_AttributeSetInstance(asiRecord);
			aiRecord.setM_Attribute_ID(attributeId);
			writeValue(aiRecord, asiField);
			InterfaceWrapperHelper.save(aiRecord);
		}

	}

}