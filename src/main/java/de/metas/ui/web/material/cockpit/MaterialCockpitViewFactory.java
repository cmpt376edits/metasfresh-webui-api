package de.metas.ui.web.material.cockpit;

import javax.annotation.Nullable;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.util.Check;
import org.adempiere.util.Services;

import com.google.common.collect.ImmutableList;

import de.metas.i18n.ITranslatableString;
import de.metas.process.IADProcessDAO;
import de.metas.process.RelatedProcessDescriptor;
import de.metas.ui.web.document.filter.DocumentFilter;
import de.metas.ui.web.document.filter.DocumentFilterDescriptorsProvider;
import de.metas.ui.web.material.cockpit.filters.MaterialCockpitFilters;
import de.metas.ui.web.material.cockpit.process.MD_Cockpit_DocumentDetail_Display;
import de.metas.ui.web.material.cockpit.process.MD_Cockpit_PricingConditions;
import de.metas.ui.web.view.CreateViewRequest;
import de.metas.ui.web.view.IView;
import de.metas.ui.web.view.IViewFactory;
import de.metas.ui.web.view.ViewFactory;
import de.metas.ui.web.view.ViewId;
import de.metas.ui.web.view.ViewProfileId;
import de.metas.ui.web.view.descriptor.ViewLayout;
import de.metas.ui.web.view.descriptor.ViewLayout.Builder;
import de.metas.ui.web.view.json.JSONViewDataType;
import de.metas.ui.web.window.datatypes.WindowId;
import de.metas.ui.web.window.descriptor.factory.standard.DefaultDocumentDescriptorFactory;
import lombok.NonNull;

/*
 * #%L
 * metasfresh-webui-api
 * %%
 * Copyright (C) 2017 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
@ViewFactory(windowId = MaterialCockpitConstants.WINDOWID_MaterialCockpitView_String, //
		viewTypes = { JSONViewDataType.grid, JSONViewDataType.includedView })
public class MaterialCockpitViewFactory
		implements IViewFactory
{
	private final MaterialCockpitRowRepository materialCockpitRowRepository;

	private final MaterialCockpitFilters materialCockpitFilters;

	public MaterialCockpitViewFactory(
			@NonNull final MaterialCockpitRowRepository materialCockpitRowRepository,
			@NonNull final MaterialCockpitFilters materialCockpitFilters,
			@NonNull final DefaultDocumentDescriptorFactory defaultDocumentDescriptorFactory)
	{
		this.materialCockpitRowRepository = materialCockpitRowRepository;
		this.materialCockpitFilters = materialCockpitFilters;

		defaultDocumentDescriptorFactory.addUnsupportedWindowId(MaterialCockpitConstants.WINDOWID_MaterialCockpitView);
	}

	@Override
	public IView createView(@NonNull final CreateViewRequest request)
	{
		assertWindowIdOfRequestIsCorrect(request);

		final DocumentFilterDescriptorsProvider filterDescriptors = materialCockpitFilters.getFilterDescriptors();
		final ImmutableList<DocumentFilter> requestFilters = materialCockpitFilters.extractDocumentFilters(request);
		final ImmutableList<DocumentFilter> filtersToUse = request.isUseAutoFilters() ? materialCockpitFilters.createAutoFilters() : requestFilters;

		final MaterialCockpitView view = MaterialCockpitView.builder()
				.viewId(request.getViewId())
				.description(ITranslatableString.empty())
				.filters(filtersToUse)
				.filterDescriptors(filterDescriptors)
				.rowsData(materialCockpitRowRepository.createRowsData(filtersToUse))
				.relatedProcessDescriptor(createProcessDescriptor(MD_Cockpit_DocumentDetail_Display.class))
				.relatedProcessDescriptor(createProcessDescriptor(MD_Cockpit_PricingConditions.class))
				.build();

		return view;
	}

	private void assertWindowIdOfRequestIsCorrect(@NonNull final CreateViewRequest request)
	{
		final ViewId viewId = request.getViewId();
		final WindowId windowId = viewId.getWindowId();

		Check.errorUnless(MaterialCockpitConstants.WINDOWID_MaterialCockpitView.equals(windowId),
				"The parameter request needs to have WindowId={}, but has {} instead; request={};",
				MaterialCockpitConstants.WINDOWID_MaterialCockpitView, windowId, request);
	}

	@Override
	public ViewLayout getViewLayout(
			@NonNull final WindowId windowId,
			@NonNull final JSONViewDataType viewDataType,
			@Nullable final ViewProfileId profileId)
	{
		Check.errorUnless(MaterialCockpitConstants.WINDOWID_MaterialCockpitView.equals(windowId),
				"The parameter windowId needs to be {}, but is {} instead; viewDataType={}; ",
				MaterialCockpitConstants.WINDOWID_MaterialCockpitView, windowId, viewDataType);

		final Builder viewlayOutBuilder = ViewLayout.builder()
				.setWindowId(windowId)
				.setHasTreeSupport(true)
				.setTreeCollapsible(true)
				.setTreeExpandedDepth(ViewLayout.TreeExpandedDepth_AllCollapsed)
				.addElementsFromViewRowClass(MaterialCockpitRow.class, viewDataType)
				.setFilters(materialCockpitFilters.getFilterDescriptors().getAll());

		return viewlayOutBuilder.build();
	}

	private final RelatedProcessDescriptor createProcessDescriptor(@NonNull final Class<?> processClass)
	{
		final IADProcessDAO adProcessDAO = Services.get(IADProcessDAO.class);
		final int processId = adProcessDAO.retrieveProcessIdByClass(processClass);
		if (processId <= 0)
		{
			throw new AdempiereException("No processId found for " + processClass);
		}

		return RelatedProcessDescriptor.builder()
				.processId(processId)
				.anyTable().anyWindow()
				.webuiQuickAction(true)
				.build();
	}

}
