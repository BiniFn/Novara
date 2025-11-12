package org.skepsun.kototoro.explore.ui.adapter

import android.view.View
import org.skepsun.kototoro.list.ui.adapter.ListHeaderClickListener
import org.skepsun.kototoro.list.ui.adapter.ListStateHolderListener

interface ExploreListEventListener : ListStateHolderListener, View.OnClickListener, ListHeaderClickListener
