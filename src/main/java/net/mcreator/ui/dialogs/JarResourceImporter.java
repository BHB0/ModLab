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

package net.mcreator.ui.dialogs;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.dialogs.file.FileDialogs;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.workspace.resources.TextureType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Imports the textures and sounds found in a mod (.jar) into the workspace. Only the resources are imported, never
 * the code. The license of the mod decides if they can be reused.
 */
public final class JarResourceImporter {

	private static final Logger LOG = LogManager.getLogger("Jar import");

	public static void show(MCreator mcreator) {
		String title = L10N.t("jar.title");

		int accept = JOptionPane.showConfirmDialog(mcreator, L10N.t("jar.license"), title, JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.WARNING_MESSAGE);
		if (accept != JOptionPane.OK_OPTION)
			return;

		File jar = FileDialogs.getOpenDialog(mcreator, new String[] { ".jar" });
		if (jar == null)
			return;

		Map<TextureType, Integer> textures = new EnumMap<>(TextureType.class);
		int sounds = 0, skipped = 0, errors = 0;

		mcreator.setCursor(new Cursor(Cursor.WAIT_CURSOR));
		try (ZipFile zip = new ZipFile(jar)) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String name = entry.getName();
				if (entry.isDirectory() || !name.startsWith("assets/"))
					continue;

				String rest = name.substring("assets/".length());
				int slash = rest.indexOf('/');
				if (slash < 0)
					continue;
				String path = rest.substring(slash + 1); // without the namespace

				File target = null;
				TextureType type = null;
				boolean sound = false;

				if (path.startsWith("textures/") && path.endsWith(".png")) {
					type = textureType(path.substring("textures/".length()));
					if (type != null) {
						File folder = mcreator.getWorkspace().getFolderManager().getTexturesFolder(type);
						if (folder != null)
							target = new File(folder, safeName(path));
					}
				} else if (path.startsWith("sounds/") && path.endsWith(".ogg")) {
					File folder = mcreator.getWorkspace().getFolderManager().getSoundsDir();
					if (folder != null) {
						target = new File(folder, safeName(path));
						sound = true;
					}
				}

				if (target == null)
					continue;
				if (target.exists()) {
					skipped++;
					continue;
				}

				try (InputStream in = zip.getInputStream(entry)) {
					target.getParentFile().mkdirs();
					Files.copy(in, target.toPath());
					if (sound)
						sounds++;
					else
						textures.merge(type, 1, Integer::sum);
				} catch (Exception e) {
					LOG.warn("Could not import {}", name, e);
					errors++;
				}
			}
		} catch (Exception e) {
			LOG.error("Could not read the jar", e);
			JOptionPane.showMessageDialog(mcreator, L10N.t("jar.failed", String.valueOf(e.getMessage())), title,
					JOptionPane.ERROR_MESSAGE);
			return;
		} finally {
			mcreator.setCursor(Cursor.getDefaultCursor());
		}

		StringBuilder summary = new StringBuilder();
		for (Map.Entry<TextureType, Integer> entry : textures.entrySet())
			summary.append("<br>").append(L10N.t("jar.count_textures", entry.getKey().getID(), entry.getValue()));
		if (sounds > 0)
			summary.append("<br>").append(L10N.t("jar.count_sounds", sounds));
		if (textures.isEmpty() && sounds == 0)
			summary.append("<br>").append(L10N.t("jar.nothing"));

		JOptionPane.showMessageDialog(mcreator, L10N.t("jar.done", summary.toString(), skipped, errors), title,
				JOptionPane.INFORMATION_MESSAGE);
	}

	/** @param path path after textures/ */
	private static TextureType textureType(String path) {
		if (path.startsWith("block/") || path.startsWith("blocks/"))
			return TextureType.BLOCK;
		if (path.startsWith("item/") || path.startsWith("items/"))
			return TextureType.ITEM;
		if (path.startsWith("entity/"))
			return TextureType.ENTITY;
		if (path.startsWith("mob_effect/") || path.startsWith("effect/"))
			return TextureType.EFFECT;
		if (path.startsWith("particle/"))
			return TextureType.PARTICLE;
		if (path.startsWith("gui/"))
			return TextureType.SCREEN;
		if (path.startsWith("models/armor/"))
			return TextureType.ARMOR;
		return null;
	}

	/** The file name only, in lower case, with characters that MCreator accepts. */
	private static String safeName(String path) {
		String name = path.substring(path.lastIndexOf('/') + 1);
		return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
	}

}
