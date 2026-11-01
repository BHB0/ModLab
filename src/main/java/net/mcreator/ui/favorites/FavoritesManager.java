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
 * Favorite mod elements of each workspace, kept in the user folder of ModLab (favorites.json).
 */
public final class FavoritesManager {

	private static final Logger LOG = LogManager.getLogger("Favorites");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type TYPE = new TypeToken<Map<String, List<String>>>() {}.getType();

	private static File file() {
		return UserFolderManager.getFileFromUserFolder("favorites.json");
	}

	private static String key(Workspace workspace) {
		return workspace.getFileManager().getWorkspaceFile().getAbsolutePath();
	}

	private static Map<String, List<String>> load() {
		File file = file();
		if (file.isFile()) {
			try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
				Map<String, List<String>> map = GSON.fromJson(reader, TYPE);
				if (map != null)
					return map;
			} catch (Exception e) {
				LOG.warn("Could not read the favorites", e);
			}
		}
		return new HashMap<>();
	}

	public static synchronized Set<String> get(Workspace workspace) {
		return new HashSet<>(load().getOrDefault(key(workspace), List.of()));
	}

	/**
	 * Adds the elements to the favorites, or removes them if they are all already favorites
	 */
	public static synchronized void toggle(Workspace workspace, Collection<String> names) {
		Map<String, List<String>> all = load();
		Set<String> favorites = new LinkedHashSet<>(all.getOrDefault(key(workspace), List.of()));
		if (favorites.containsAll(names))
			favorites.removeAll(names);
		else
			favorites.addAll(names);
		all.put(key(workspace), new ArrayList<>(favorites));

		try {
			Files.writeString(file().toPath(), GSON.toJson(all, TYPE), StandardCharsets.UTF_8);
		} catch (Exception e) {
			LOG.warn("Could not save the favorites", e);
		}
	}

}
