package com.hyc.m3u8downloader

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.databinding.DataBindingUtil
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.annotation.RequiresApi
import android.support.v4.app.ActivityCompat
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.FileProvider
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SimpleItemAnimator
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import com.hyc.m3u8downloader.databinding.ActivityMainBinding
import com.hyc.m3u8downloader.model.MainViewModel
import com.hyc.m3u8downloader.model.MediaItem
import com.hyc.m3u8downloader.utils.NetStateChangeReceiver
import com.hyc.m3u8downloader.utils.hasEnoughSpace
import com.hyc.m3u8downloader.view.*
import java.io.File

class MainActivity : AppCompatActivity(), MediaController {

    private val SDCARD_PERMISSION_R = Manifest.permission.READ_EXTERNAL_STORAGE
    private val SDCARD_PERMISSION_W = Manifest.permission.WRITE_EXTERNAL_STORAGE
    private val PERMISSION_NET = "android.Manifest.permission.ACCESS_NETWORK_STATE"
    private val mRequestCode = 100
    private lateinit var mBinding: ActivityMainBinding
    private var mWidth: Int = 0
    private val delayTime = 80L
    private var menuShowing = false
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        mBinding.controller = this
        mBinding.model = ViewModelProviders.of(this).get(MainViewModel::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this@MainActivity,
                    arrayOf(SDCARD_PERMISSION_R, SDCARD_PERMISSION_W, PERMISSION_NET), 100)
        }
        mBinding.model!!.loadingFormDB(this)
        mBinding.setLifecycleOwner(this)
        mBinding.model!!.adapter.let {
            it.observe(this, Observer<MainAdapter2> { t ->
                t?.let {
                    t.setOnClickListener(this@MainActivity)
                    t.setOnLongClickListener(this@MainActivity)
                }
            })
        }
        findViewById<RecyclerView>(R.id.rv_content).let {
            var manager = LinearLayoutManager(this)
            it.layoutManager = manager
            (it.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        }
        Log.e("hyc-notify", NotificationManagerCompat.from(this).areNotificationsEnabled().toString())
//        NotificationPageHelper.open(this)
        startService(Intent(this, ForegroundService::class.java))
    }

    override fun onItemClicked(item: MutableLiveData<MediaItem>) {
        when (item.value!!.state) {
            DownloadState.DOWNLOADING, DownloadState.WAITING -> mBinding.model!!.pauseItem(item)
            DownloadState.STOPPED, DownloadState.FAiLED -> {
                when (NetStateChangeReceiver.getInstance().getNetState()) {
                    NetStateChangeReceiver.STATE_NO_CONNECT -> Toast.makeText(this, "当前无法连接网络，请连接后再试", Toast.LENGTH_LONG).show()
                    NetStateChangeReceiver.STATE_CONNECT_WIFI -> mBinding.model!!.resumeItem(item)
                    NetStateChangeReceiver.STATE_CONNECT_OTHER -> showNotWifiDialog(this, object : PositiveClickListener {
                        override fun onPositiveClicked() {
                            mBinding.model!!.resumeItem(item)
                        }
                    })
                }
            }
            DownloadState.SUCCESS -> goToPlay(item)
        }
    }

    override fun onItemLongClicked(item: MutableLiveData<MediaItem>) {
        showDeleteItemDialog(this, object : PositiveClickListener {
            override fun onPositiveClicked() {
                mBinding.model!!.deleteItem(item)
            }
        })
    }

    private fun goToPlay(item: MutableLiveData<MediaItem>) {
        val mp4Path = item.value!!.mp4Path
        if (TextUtils.isEmpty(mp4Path) || !File(mp4Path).exists()) {
            showReDownloadDialog(this, object : PositiveClickListener {
                override fun onPositiveClicked() {
                    mBinding.model!!.reDownload(item)
                }
            })
        } else {
            val intent = Intent(Intent.ACTION_VIEW)
            val type = "video/*"
            var uri: Uri
            uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val file = File(mp4Path)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                FileProvider.getUriForFile(this, "com.hyc.m3u8downloader.fileProvider", file)
            } else {
                Uri.parse(mp4Path)
            }
            intent.setDataAndType(uri, type)
            startActivity(intent)
        }
    }

    override fun onFabClicked(view: View) {
        if (menuShowing) {
            closeMenu()
        } else {
            showMenu()
        }
    }

    override fun onDeleteAllClicked(view: View) {
        Log.e("hyc-fab", "deleteAll")
        showDeleteAllDialog(this, object : PositiveClickListener {
            override fun onPositiveClicked() {
                mBinding.model!!.deleteAll()
            }
        })
        closeMenu()
    }

    override fun onBackPressed() {
        if (menuShowing) {
            closeMenu()
            return
        }
        if (NetStateChangeReceiver.getInstance().backgroundDownload) {
            goHome()
            return
        }
        super.onBackPressed()

    }

    private fun goHome() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        intent.addCategory(Intent.CATEGORY_HOME)
        startActivity(intent)

    }

    override fun finish() {
        super.finish()
        mBinding.model!!.pauseAll()
    }

    override fun onCreateNewMediaClicked(view: View) {
        Log.e("hyc-fab", "createNewMedia")
        if (hasEnoughSpace()) {
            when (NetStateChangeReceiver.getInstance().getNetState()) {
                NetStateChangeReceiver.STATE_NO_CONNECT -> Toast.makeText(this, "当前无法连接网络，请连接后再试", Toast.LENGTH_LONG).show()
                NetStateChangeReceiver.STATE_CONNECT_WIFI -> showAddDialog()
                NetStateChangeReceiver.STATE_CONNECT_OTHER -> showNotWifiDialog(this, object : PositiveClickListener {
                    override fun onPositiveClicked() {
                        showAddDialog()
                    }
                })
            }
        } else {
            showSpaceNotEnoughDialog(this)
        }
        closeMenu()
    }

    private fun showAddDialog() {
        showAddDialog(this, object : GetTextListener {
            override fun onGetText(url: String) {
                mBinding.model!!.createItem("1111", url)
            }
        })
    }

    override fun onPauseAllClicked(view: View) {
        Log.e("hyc-fab", "pauseAll")
        mBinding.model!!.pauseAll()
        closeMenu()
    }

    override fun onResumeAllClicked(view: View) {
        Log.e("hyc-fab", "resumeAll")
        if (hasEnoughSpace()) {
            mBinding.model!!.resumeAll()
        } else {
            showSpaceNotEnoughDialog(this)
        }
        closeMenu()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (mRequestCode == requestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "抱歉，未获取到读写权限，无法使用该应用", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    }

    private fun showMenu() {
        menuShowing = true
        if (mWidth == 0) {
            mWidth = resources.displayMetrics.widthPixels
            mBinding.llStartAll.translationX = mWidth - mBinding.llStartAll.x
            mBinding.llCreate.translationX = mWidth - mBinding.llCreate.x
            mBinding.llDeleteAll.translationX = mWidth - mBinding.llDeleteAll.x
            mBinding.llPauseAll.translationX = mWidth - mBinding.llPauseAll.x
        }
        mBinding.flBack.isClickable = true
        mBinding.flBack.isFocusable = true
        mBinding.flBack.animate().alpha(1f).setDuration(300 + 3 * delayTime).start()
        mBinding.fabMenu.animate().rotation(45f).setDuration(300).setInterpolator(OvershootInterpolator()).start()
        mBinding.llStartAll.animate().setDuration(300).translationX(0f).alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(delayTime).start()
        mBinding.llCreate.animate().setDuration(300).translationX(0f).alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(delayTime * 3).start()
        mBinding.llDeleteAll.animate().setDuration(300).translationX(0f).alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(delayTime * 2).start()
        mBinding.llPauseAll.animate().setDuration(300).translationX(0f).alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(0).start()
    }

    private fun closeMenu() {
        menuShowing = false
        mBinding.flBack.animate().alpha(0f).setDuration(300 + 3 * delayTime).start()
        mBinding.flBack.isClickable = false
        mBinding.flBack.isFocusable = false
        mBinding.fabMenu.animate().rotation(0f).setDuration(300).setInterpolator(OvershootInterpolator()).start()
        mBinding.llStartAll.animate().setDuration(300).translationX(mWidth - mBinding.llStartAll.x).alpha(0f).scaleX(0f).scaleY(0f).setStartDelay(delayTime * 2).start()
        mBinding.llCreate.animate().setDuration(300).translationX(mWidth - mBinding.llCreate.x).alpha(0f).scaleX(0f).scaleY(0f).setStartDelay(0).start()
        mBinding.llDeleteAll.animate().setDuration(300).translationX(mWidth - mBinding.llDeleteAll.x).alpha(0f).scaleX(0f).scaleY(0f).setStartDelay(delayTime).start()
        mBinding.llPauseAll.animate().setDuration(300).translationX(mWidth - mBinding.llPauseAll.x).alpha(0f).scaleX(0f).scaleY(0f).setStartDelay(delayTime * 3).start()
    }


}
