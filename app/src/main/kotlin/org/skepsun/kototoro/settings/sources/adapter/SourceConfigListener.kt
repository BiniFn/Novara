package org.skepsun.kototoro.settings.sources.adapter

import org.skepsun.kototoro.core.ui.list.OnTipCloseListener
import org.skepsun.kototoro.settings.sources.model.SourceConfigItem

interface SourceConfigListener : OnTipCloseListener<SourceConfigItem.Tip> {

	fun onItemSettingsClick(item: SourceConfigItem.SourceItem)

	fun onItemLiftClick(item: SourceConfigItem.SourceItem)

	fun onItemShortcutClick(item: SourceConfigItem.SourceItem)

	fun onItemPinClick(item: SourceConfigItem.SourceItem)

	fun onItemEnabledChanged(item: SourceConfigItem.SourceItem, isEnabled: Boolean)

	fun onItemClick(item: SourceConfigItem.SourceItem)
}
