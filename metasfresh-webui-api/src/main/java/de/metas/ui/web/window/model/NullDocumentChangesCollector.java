package de.metas.ui.web.window.model;

import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import de.metas.ui.web.window.datatypes.DocumentPath;

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

public final class NullDocumentChangesCollector implements IDocumentChangesCollector
{
	public static final transient NullDocumentChangesCollector instance = new NullDocumentChangesCollector();

	private NullDocumentChangesCollector()
	{
		super();
	}

	@Override
	public Set<String> getFieldNames(final DocumentPath documentPath)
	{
		return ImmutableSet.of();
	}

	@Override
	public boolean isEmpty()
	{
		return true;
	}

	@Override
	public Map<DocumentPath, DocumentChanges> getDocumentChangesByPath()
	{
		return ImmutableMap.of();
	}

	@Override
	public void collectValueChanged(final IDocumentFieldView documentField, final ReasonSupplier reason)
	{
		// do nothing
	}

	@Override
	public void collectReadonlyChanged(final IDocumentFieldView documentField, final ReasonSupplier reason)
	{
		// do nothing
	}

	@Override
	public void collectMandatoryChanged(final IDocumentFieldView documentField, final ReasonSupplier reason)
	{
		// do nothing
	}

	@Override
	public void collectDisplayedChanged(final IDocumentFieldView documentField, final ReasonSupplier reason)
	{
		// do nothing
	}

	@Override
	public void collectLookupValuesStaled(final IDocumentFieldView documentField, final ReasonSupplier reason)
	{
		// do nothing
	}

	@Override
	public void collectFrom(final IDocumentChangesCollector fromCollector)
	{
		// do nothing
	}

	@Override
	public boolean collectFrom(final Document document, final ReasonSupplier reason)
	{
		return false; // nothing collected
	}
}