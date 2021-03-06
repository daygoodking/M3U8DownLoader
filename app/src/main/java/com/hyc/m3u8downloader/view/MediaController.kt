package com.hyc.m3u8downloader.view

import android.view.View

interface MediaController:MainAdapter2.OnItemClickListener,MainAdapter2.OnItemLongClickListener {
    fun onCreateNewMediaClicked(view: View)
    fun onPauseAllClicked(view: View)
    fun onResumeAllClicked(view: View)
    fun onDeleteAllClicked(view: View)
    fun onFabClicked(view: View)
    fun onSettingClicked(view:View)
}