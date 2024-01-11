package com.example.prowd_android_template.activity_set.activity_easy_lut_sample

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.example.prowd_android_template.abstract_class.AbstractProwdRecyclerViewAdapter
import com.example.prowd_android_template.custom_view.DialogConfirm
import com.example.prowd_android_template.custom_view.DialogProgressLoading
import com.example.prowd_android_template.common_shared_preference_wrapper.CurrentLoginSessionInfoSpw
import com.example.prowd_android_template.custom_view.DialogBinaryChoose
import com.example.prowd_android_template.custom_view.DialogRadioButtonChoose
import com.example.prowd_android_template.repository.RepositorySet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

class ActivityEasyLutSampleViewModel(application: Application) :
    AndroidViewModel(application) {
    // <멤버 변수 공간>
    private val applicationMbr = application

    // (repository 모델)
    private val repositorySetMbr: RepositorySet = RepositorySet.getInstance(applicationMbr)

    // (스레드 풀)
    var executorServiceMbr: ExecutorService? = Executors.newCachedThreadPool()

    // (SharedPreference 객체)
    // 현 로그인 정보 접근 객체
    val currentLoginSessionInfoSpwMbr: CurrentLoginSessionInfoSpw =
        CurrentLoginSessionInfoSpw(application)
    val thisSpw: ActivityEasyLutSampleSpw = ActivityEasyLutSampleSpw(application)

    // (데이터)
    // 이 화면에 도달한 유저 계정 고유값(세션 토큰이 없다면 비회원 상태)
    var currentUserSessionTokenMbr: String? = null

    // (플래그 데이터)
    // 데이터 수집 등, 첫번째에만 발동
    var isDataFirstLoadingMbr = true

    // 리사이클러 뷰 아이템 조작 싱크를 위한 세마포어(리포지토리 요청 같은 비동기 상황을 가정하자면 필수 처리)
    // 같은 아이템이라도 헤더 / 푸터와 일반 아이템은 내부적으로 싱크 처리가 되어있기에 아이템 리스트에만 적용
    val recyclerViewAdapterItemSemaphore = Semaphore(1)

    // 중복 요청 금지를 위한 상태 플래그
    var isRecyclerViewItemLoadingMbr = false
        @Synchronized get
        @Synchronized set


    // ---------------------------------------------------------------------------------------------
    // <뷰모델 라이브데이터 공간>
    // 로딩 다이얼로그 출력 정보
    val progressLoadingDialogInfoLiveDataMbr: MutableLiveData<DialogProgressLoading.DialogInfoVO?> =
        MutableLiveData(null)

    // 선택 다이얼로그 출력 정보
    val binaryChooseDialogInfoLiveDataMbr: MutableLiveData<DialogBinaryChoose.DialogInfoVO?> =
        MutableLiveData(null)

    // 확인 다이얼로그 출력 정보
    val confirmDialogInfoLiveDataMbr: MutableLiveData<DialogConfirm.DialogInfoVO?> =
        MutableLiveData(null)

    // 라디오 버튼 다이얼로그 출력 정보
    val radioButtonDialogInfoLiveDataMbr: MutableLiveData<DialogRadioButtonChoose.DialogInfoVO?> =
        MutableLiveData(null)

    val filteredImage1LiveDataMbr: MutableLiveData<Bitmap?> = MutableLiveData(null)

    val filteredImage2LiveDataMbr: MutableLiveData<Bitmap?> = MutableLiveData(null)

    val filteredImage3LiveDataMbr: MutableLiveData<Bitmap?> = MutableLiveData(null)

    // (RecyclerViewAdapter 데이터)
    // recyclerView 내에서 사용되는 뷰모델 데이터
    val recyclerViewAdapterItemListLiveDataMbr: MutableLiveData<ArrayList<AbstractProwdRecyclerViewAdapter.AdapterItemAbstractVO>> =
        MutableLiveData(ArrayList())


    // ---------------------------------------------------------------------------------------------
    // <클래스 생명주기 공간>
    override fun onCleared() {
        executorServiceMbr?.shutdown()
        executorServiceMbr = null
        super.onCleared()
    }


    // ---------------------------------------------------------------------------------------------
    // <공개 메소드 공간>


    // ---------------------------------------------------------------------------------------------
    // <비공개 메소드 공간>
}