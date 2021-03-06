package de.metas.ui.web.menu;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.adempiere.ad.security.IUserRolePermissionsDAO;
import org.adempiere.ad.security.UserRolePermissionsKey;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.user.api.IUserMenuFavoritesDAO;
import org.adempiere.util.Services;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.google.common.base.MoreObjects;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import de.metas.logging.LogManager;
import de.metas.ui.web.session.UserSession;

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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

@Repository
public class MenuTreeRepository implements MenuNodeFavoriteProvider
{
	private static final transient Logger logger = LogManager.getLogger(MenuTreeRepository.class);

	@Autowired
	private UserSession userSession;

	private final LoadingCache<MenuTreeKey, MenuTree> menuTrees = CacheBuilder.newBuilder().expireAfterAccess(1, TimeUnit.HOURS).build(new CacheLoader<MenuTreeKey, MenuTree>()
	{

		@Override
		public MenuTree load(final MenuTreeKey key)
		{
			return MenuTreeLoader.newInstance().setUserRolePermissionsKey(key.getUserRolePermissionsKey()).setAD_Language(key.getAD_Language()).load();
		}
	});

	private final LoadingCache<Integer, UserMenuFavorites> userMenuFavoritesByUserId = CacheBuilder.newBuilder().expireAfterAccess(1, TimeUnit.HOURS).build(new CacheLoader<Integer, UserMenuFavorites>()
	{

		@Override
		public UserMenuFavorites load(final Integer adUserId)
		{
			return retrieveFavoriteMenuIds(adUserId);
		}
	});

	public MenuTree getUserSessionMenuTree()
	{
		final UserRolePermissionsKey userRolePermissionsKey = userSession.getUserRolePermissionsKey();
		final String adLanguage = userSession.getAD_Language();
		return getMenuTree(userRolePermissionsKey, adLanguage);
	}

	public MenuTree getMenuTree(final UserRolePermissionsKey userRolePermissionsKey, final String adLanguage)
	{
		try
		{
			final MenuTreeKey key = new MenuTreeKey(userRolePermissionsKey, adLanguage);
			MenuTree menuTree = menuTrees.get(key);

			//
			// If menuTree's version is not the current one, try re-acquiring it.
			int retry = 3;
			final long currentVersion = Services.get(IUserRolePermissionsDAO.class).getCacheVersion();
			while (menuTree.getVersion() != currentVersion)
			{
				menuTrees.invalidate(key);
				menuTree = menuTrees.get(key);

				retry--;
				if (retry <= 0)
				{
					break;
				}
			}
			if (menuTree.getVersion() != currentVersion)
			{
				logger.warn("Could not acquire menu tree version {}. Returning what we got... \nmenuTree: {}\nkey={}", currentVersion, menuTree, key);
			}

			return menuTree;
		}
		catch (final ExecutionException e)
		{
			throw AdempiereException.wrapIfNeeded(e);
		}
	}

	public void cacheReset()
	{
		menuTrees.invalidateAll();
		menuTrees.cleanUp();

		userMenuFavoritesByUserId.invalidateAll();
		userMenuFavoritesByUserId.cleanUp();
	}

	public UserMenuFavorites getUserMenuFavorites()
	{
		final int adUserId = userSession.getAD_User_ID();
		try
		{
			return userMenuFavoritesByUserId.get(adUserId);
		}
		catch (final ExecutionException ex)
		{
			throw AdempiereException.wrapIfNeeded(ex);
		}
	}

	private UserMenuFavorites retrieveFavoriteMenuIds(final int adUserId)
	{
		final List<Integer> adMenuIds = Services.get(IUserMenuFavoritesDAO.class).retrieveMenuIdsForUser(adUserId);

		return UserMenuFavorites.builder()
				.adUserId(adUserId)
				.addMenuIds(adMenuIds)
				.build();
	}

	public void setFavorite(final MenuNode menuNode, final boolean favorite)
	{
		final int adMenuId = menuNode.getAD_Menu_ID();

		final UserMenuFavorites userMenuFavorites = getUserMenuFavorites();
		final int adUserId = userMenuFavorites.getAdUserId();

		// Update in database first
		if (favorite)
		{
			Services.get(IUserMenuFavoritesDAO.class).add(adUserId, adMenuId);
		}
		else
		{
			Services.get(IUserMenuFavoritesDAO.class).remove(adUserId, adMenuId);
		}

		// Update model
		userMenuFavorites.setFavorite(adMenuId, favorite);
	}

	@Override
	public boolean isFavorite(final MenuNode menuNode)
	{
		return getUserMenuFavorites().isFavorite(menuNode);
	}

	private static final class MenuTreeKey
	{
		private final UserRolePermissionsKey userRolePermissionsKey;
		private final String adLanguage;

		private MenuTreeKey(final UserRolePermissionsKey userRolePermissionsKey, final String adLanguage)
		{
			super();
			this.userRolePermissionsKey = userRolePermissionsKey;
			this.adLanguage = adLanguage;
		}

		@Override
		public String toString()
		{
			return MoreObjects.toStringHelper(this)
					.add("adLanguage", adLanguage)
					.addValue(userRolePermissionsKey)
					.toString();
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(userRolePermissionsKey, adLanguage);
		}

		@Override
		public boolean equals(final Object obj)
		{
			if (this == obj)
			{
				return true;
			}
			if (obj instanceof MenuTreeKey)
			{
				final MenuTreeKey other = (MenuTreeKey)obj;
				return Objects.equals(userRolePermissionsKey, other.userRolePermissionsKey)
						&& Objects.equals(adLanguage, other.adLanguage);
			}
			else
			{
				return false;
			}
		}

		public UserRolePermissionsKey getUserRolePermissionsKey()
		{
			return userRolePermissionsKey;
		}

		public String getAD_Language()
		{
			return adLanguage;
		}
	}

	private static final class UserMenuFavorites
	{
		private static final Builder builder()
		{
			return new Builder();
		}

		private final int adUserId;
		private final Set<Integer> menuIds = ConcurrentHashMap.newKeySet();

		private UserMenuFavorites(final Builder builder)
		{
			adUserId = builder.adUserId;
			if (adUserId < 0)
			{
				throw new IllegalArgumentException("adUserId not set");
			}

			menuIds.addAll(builder.menuIds);
		}

		public int getAdUserId()
		{
			return adUserId;
		}

		public boolean isFavorite(final MenuNode menuNode)
		{
			return menuIds.contains(menuNode.getAD_Menu_ID());
		}

		public void setFavorite(final int adMenuId, final boolean favorite)
		{
			if (favorite)
			{
				menuIds.add(adMenuId);
			}
			else
			{
				menuIds.remove(adMenuId);
			}
		}

		public static class Builder
		{
			private int adUserId = -1;
			private final Set<Integer> menuIds = new HashSet<>();

			private Builder()
			{
			}

			public MenuTreeRepository.UserMenuFavorites build()
			{
				return new UserMenuFavorites(this);
			}

			public Builder adUserId(final int adUserId)
			{
				this.adUserId = adUserId;
				return this;
			}

			public Builder addMenuIds(final List<Integer> adMenuIds)
			{
				if (adMenuIds.isEmpty())
				{
					return this;
				}

				menuIds.addAll(adMenuIds);
				return this;
			}
		}

	}
}
