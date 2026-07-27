/*
 *  This file (LanguagesManager.java) is a part of project XConomy
 *  Copyright (C) YiC and contributors
 *
 *  This program is free software: you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the
 *  Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package me.yic.xconomy.lang;

import me.yic.xconomy.adapter.comp.CConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LanguagesManager {
    public static CConfig messageFile;

    public static void compare(String lang, File f) {
        messageFile = new CConfig("/lang", "/" + lang.toLowerCase() + ".yml");

        List<String> messages = index();
        for (String message : messages) {
            boolean renew = false;
            if (!MessagesManager.messageFile.contains(message)) {
                renew = true;
                MessagesManager.messageFile.createSection(message);
                MessagesManager.messageFile.set(message, messageFile.getString(message));
            }

            try {
                if (renew) {
                    MessagesManager.messageFile.save();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static List<String> index() {
        List<String> ll = new ArrayList<>();
        ll.add("translation_authors");
        ll.add("prefix");
        ll.add("balance");
        ll.add("balance_other");
        ll.add("top_title");
        ll.add("sum_text");
        ll.add("top_text");
        ll.add("top_subtitle");
        ll.add("top_nodata");
        ll.add("top_out");
        ll.add("top_hidden");
        ll.add("top_displayed");
        ll.add("staff_hidden");
        ll.add("staff_displayed");
        ll.add("pay");
        ll.add("pay_receive");
        ll.add("pay_fail");
        ll.add("pay_self");
        ll.add("paytoggle_true");
        ll.add("paytoggle_false");
        ll.add("paytoggle_other_true");
        ll.add("paytoggle_other_false");
        ll.add("no_account");
        ll.add("invalid_amount");
        ll.add("invalid_usage");
        ll.add("usage_xconomy_help");
        ll.add("usage_xconomy_reload");
        ll.add("usage_xconomy_deldata");
        ll.add("usage_xconomy_migrate");
        ll.add("usage_balance_look");
        ll.add("usage_balance_subcommand");
        ll.add("usage_balance_single");
        ll.add("usage_balance_batch");
        ll.add("usage_pay");
        ll.add("usage_paytoggle");
        ll.add("usage_paypermission");
        ll.add("usage_balancetop_page");
        ll.add("usage_balancetop_visibility");
        ll.add("usage_track");
        ll.add("usage_track_cleanup");
        ll.add("usage_track_self");
        ll.add("usage_track_other");
        ll.add("usage_track_flow");
        ll.add("over_maxnumber");
        ll.add("over_maxnumber_receive");
        ll.add("money_give");
        ll.add("money_give_receive");
        ll.add("money_take");
        ll.add("money_take_fail");
        ll.add("money_take_receive");
        ll.add("money_set");
        ll.add("money_set_receive");
        ll.add("no_receive_permission");
        ll.add("no_permission");
        ll.add("no_data");
        ll.add("no_online_players");
        ll.add("all_players");
        ll.add("online_players");
        ll.add("delete_data");
        ll.add("delete_data_admin");
        ll.add("global_permissions_change");
        ll.add("personal_permissions_change");
        ll.add("help_title_full");
        ll.add("help_footer");
        ll.add("help1");
        ll.add("help2");
        ll.add("help3");
        ll.add("help4");
        ll.add("help5");
        ll.add("help6");
        ll.add("help7");
        ll.add("help8");
        ll.add("help9");
        ll.add("help10");
        ll.add("help11");
        ll.add("help12");
        ll.add("help13");
        ll.add("help14");
        // Transaction tracking
        ll.add("help15");
        ll.add("help16");
        ll.add("help17");
        ll.add("help18");
        ll.add("help19");
        ll.add("help20");
        ll.add("help21");
        ll.add("track_income_title");
        ll.add("track_expense_title");
        ll.add("track_income_text");
        ll.add("track_expense_text");
        ll.add("track_no_records");
        ll.add("track_disabled");
        ll.add("track_statistics");
        ll.add("track_footer");
        ll.add("track_detail_not_found");
        ll.add("track_detail_title");
        ll.add("track_detail_amount");
        ll.add("track_detail_parties");
        ll.add("track_detail_context");
        ll.add("track_detail_metadata");
        ll.add("track_detail_comment");
        ll.add("track_detail_command");
        ll.add("track_detail_flow");
        ll.add("track_not_available");
        ll.add("track_cleanup_success");
        ll.add("track_cleanup_confirm");
        ll.add("track_type_pay_send");
        ll.add("track_type_pay_receive");
        ll.add("track_type_admin_give");
        ll.add("track_type_admin_take");
        ll.add("track_type_admin_set");
        ll.add("track_type_plugin_give");
        ll.add("track_type_plugin_take");
        ll.add("track_type_plugin_set");
        ll.add("track_type_system");
        ll.add("track_type_admin");
        ll.add("track_type_unknown");
        ll.add("track_type_with_reason");
        ll.add("console_name");
        ll.add("track_view_other_no_permission");
        ll.add("tab_amount");
        ll.add("tab_transaction_id");
        return ll;

    }

    public static void translateFile(String string, File file) {
        try {
            FileOutputStream f = new FileOutputStream(file, true);
            f.write(string.getBytes());
            f.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void translatorName(File file) {
        String ta = messageFile.getString("translation_authors");
        if (!ta.equalsIgnoreCase("") && !ta.equalsIgnoreCase("none")) {
            translateFile("#========== Translation_authors - " + ta + " ==========", file);
        }
    }
}
