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
import net.mcreator.ui.init.L10N;
import net.mcreator.workspace.ShareableZIPManager;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Safe start of a migration to another Minecraft version: a backup of the workspace is made first, then the
 * workspace settings (where the generator is chosen) are opened.
 */
public final class MigrationHelper {

	public static void start(MCreator mcreator) {
		String title = L10N.t("migrate.title");

		int choice = JOptionPane.showConfirmDialog(mcreator, L10N.t("migrate.message"), title,
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE);
		if (choice != JOptionPane.OK_OPTION)
			return;

		File folder = new File(System.getProperty("user.home"), "ModLabBackups");
		if (!folder.isDirectory() && !folder.mkdirs()) {
			JOptionPane.showMessageDialog(mcreator, L10N.t("migrate.backup_failed"), title, JOptionPane.ERROR_MESSAGE);
			return;
		}

		String stamp = new SimpleDateFormat("yyyyMMdd-HHmm").format(new Date());
		File backup = new File(folder, mcreator.getWorkspaceFolder().getName() + "-before-migration-" + stamp + ".zip");
		ShareableZIPManager.exportZIP(L10N.t("migrate.title"), backup, mcreator, true);

		if (!backup.isFile() || backup.length() == 0) {
			JOptionPane.showMessageDialog(mcreator, L10N.t("migrate.backup_failed"), title, JOptionPane.ERROR_MESSAGE);
			return;
		}

		JOptionPane.showMessageDialog(mcreator, L10N.t("migrate.backup_done", backup.getAbsolutePath()), title,
				JOptionPane.INFORMATION_MESSAGE);
		mcreator.getActionRegistry().workspaceSettings.actionPerformed(
				new ActionEvent(mcreator, ActionEvent.ACTION_PERFORMED, ""));
	}

}
