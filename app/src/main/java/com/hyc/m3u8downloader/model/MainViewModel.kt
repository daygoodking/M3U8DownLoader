package com.hyc.m3u8downloader.model

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.MutableLiveData
import android.content.Context
import com.hyc.m3u8downloader.DownloadManager
import com.hyc.m3u8downloader.view.MainAdapter
import com.hyc.m3u8downloader.view.MainAdapter2
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.ObservableOnSubscribe
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers

class MainViewModel(app: Application) : AndroidViewModel(app) {
    var adapter: MutableLiveData<MainAdapter2> = MutableLiveData()
    var item: MutableLiveData<MediaItem> = MutableLiveData()

    fun loadingFormDB(context: Context) {
        Observable.create(ObservableOnSubscribe<MainAdapter2> { emitter ->
            emitter.onNext(MainAdapter2(DownloadManager.getInstance().getAllMedia(), context))
        }).subscribeOn(Schedulers.newThread()).observeOn(AndroidSchedulers.mainThread()).subscribe {
            adapter.postValue(it)
        }
//        DownloadManager.getInstance().getAllMedia().map { t ->
//            var target = ArrayList<MutableLiveData<MediaItem>>()
//            if (t != null && !t.isEmpty()) {
//                for (item in t) {
//                    var media = item.mediaItem
//                    media!!.list = item.tsFiles
//                    var data = MyLiveData()
//                    data.postValue(media)
//                    target.add(data)
//                }
//            }
//            MainAdapter2(target, context)
//        }.subscribeOn(Schedulers.newThread()).observeOn(AndroidSchedulers.mainThread()).subscribe {
//            adapter.postValue(it)
//        }
    }

    fun deleteAll() {
        DownloadManager.getInstance().deleteAll()
    }

    fun pauseAll() {
        DownloadManager.getInstance().pauseAll()
    }

    fun resumeAll() {
        DownloadManager.getInstance().startAll()
    }

    fun pauseItem(item: MutableLiveData<MediaItem>) {
        DownloadManager.getInstance().pauseItem(item)
    }

    fun resumeItem(item: MutableLiveData<MediaItem>) {
        DownloadManager.getInstance().resumeItem(item)
    }

    fun createItem(name: String, url: String) {
        adapter.value!!.addItem(DownloadManager.getInstance().createNew(url, name))
    }

}