package de.metas.ui.web.board;

import java.util.Collection;

import org.adempiere.exceptions.AdempiereException;

import com.google.common.collect.ImmutableMap;

import de.metas.i18n.ITranslatableString;
import de.metas.ui.web.window.datatypes.WindowId;
import de.metas.ui.web.window.descriptor.LookupDescriptorProvider;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

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

@Builder
@Value
public final class BoardDescriptor
{
	private final int boardId;
	@NonNull
	private final ITranslatableString caption;
	@NonNull
	private final String websocketEndpoint;

	@Singular
	private final ImmutableMap<Integer, BoardLaneDescriptor> lanes;

	@Singular("cardFieldByFieldName")
	final ImmutableMap<String, BoardCardFieldDescriptor> cardFieldsByFieldName;

	// Source document info
	@NonNull
	private final WindowId documentWindowId;
	@NonNull
	private LookupDescriptorProvider documentLookupDescriptorProvider;

	// Source record mapping
	@NonNull
	private final String tableName;
	@NonNull
	private final String tableAlias;
	@NonNull
	private final String keyColumnName;
	private final int adValRuleId;
	@NonNull
	private final String userIdColumnName;

	public void assertLaneIdExists(final int laneId)
	{
		if (lanes.get(laneId) == null)
		{
			throw new AdempiereException("Lane ID=" + laneId + " found for board ID=" + getBoardId())
					.setParameter("board", this)
					.setParameter("laneId", laneId);
		}
	}

	public Collection<BoardCardFieldDescriptor> getCardFields()
	{
		return cardFieldsByFieldName.values();
	}

	public BoardCardFieldDescriptor getCardFieldByName(final String fieldName)
	{
		final BoardCardFieldDescriptor cardField = cardFieldsByFieldName.get(fieldName);
		if (cardField == null)
		{
			throw new AdempiereException("No card field found for " + fieldName)
					.setParameter("board", this);
		}
		return cardField;
	}
}