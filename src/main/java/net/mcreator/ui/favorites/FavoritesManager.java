/*
 * MCreator (https://mcreator.net/)
 * Copyright (C) 2012-2020, Pylo
 * Copyright (C) 2020-2026, Pylo, opensource contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.mcreator.ui.favorites;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.mcreator.io.UserFolderManager;
import net.mcreator.workspace.Workspace;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Favorite mod elements of each workspace, kept in the user folder of ModLab (favorites.json). The content is kept in
 * memory, because the list asks for it for every element it draws.
 */
public final class FavoritesManager {

	private static final Logger LOG = LogManager.getLogger("Favorites");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type TYPE = new TypeToken<Map<String, List<String>>>() {}.getType();

	private static Map<String, List<String>> data;

	private static File file() {
		return UserFolderManager.getFileFromUserFolder("favorites.json");
	}

	private static String key(Workspace workspace) {
		return workspace.getFileManager().getWorkspaceFile().getAbsolutePath();
	}

	private static synchronized Map<String, List<String>> data() {
		if (data == null) {
			data = new HashMap<>();
			File file = file();
			if (file.isFile()) {
				try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
					Map<String, List<String>> loaded = GSON.fromJson(reader, TYPE);
					if (loaded != null)
						data = loaded;
				} catch (Exception e) {
					LOG.warn("Could not read the favorites", e);
				}
			}
		}
		return data;
	}

	public static synchronized boolean isFavorite(Workspace workspace, String name) {
		return data().getOrDefault(key(workspace), List.of()).contains(name);
	}

	public static synchronized Set<String> get(Workspace workspace) {
		return new HashSet<>(data().getOrDefault(key(workspace), List.of()));
	}

	/**
	 * Adds the elements to the favorites, or removes them if they are all already favorites
	 *
	 * @return true if the elements are now favorites
	 */
	public static synchronized boolean toggle(Workspace workspace, Collection<String> names) {
		Set<String> favorites = new LinkedHashSet<>(data().getOrDefault(key(workspace), List.of()));
		boolean added = !favorites.containsAll(names);
		if (added)
			favorites.addAll(names);
		else
			favorites.removeAll(names);
		data().put(key(workspace), new ArrayList<>(favorites));

		try {
			Files.writeString(file().toPath(), GSON.toJson(data(), TYPE), StandardCharsets.UTF_8);
		} catch (Exception e) {
			LOG.warn("Could not save the favorites", e);
		}
		return added;
	}

}
