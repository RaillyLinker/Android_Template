package com.example.prowd_android_template.activity_set.activity_user_login_sample

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.prowd_android_template.abstract_class.AbstractProwdRecyclerViewAdapter
import com.example.prowd_android_template.abstract_class.InterfaceDialogInfoVO
import com.example.prowd_android_template.activity_set.activity_email_user_join_sample.ActivityEmailUserJoinSample
import com.example.prowd_android_template.common_shared_preference_wrapper.CurrentLoginSessionInfoSpw
import com.example.prowd_android_template.custom_view.DialogBinaryChoose
import com.example.prowd_android_template.custom_view.DialogConfirm
import com.example.prowd_android_template.custom_view.DialogProgressLoading
import com.example.prowd_android_template.custom_view.DialogRadioButtonChoose
import com.example.prowd_android_template.databinding.ActivityUserLoginSampleBinding
import com.example.prowd_android_template.repository.RepositorySet
import com.example.prowd_android_template.repository.database_room.tables.TestUserInfoTable
import com.example.prowd_android_template.util_class.ThreadConfluenceObj
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore


// 유저 로그인 샘플
// : SNS OAuth 로그인은 서비스시 따로 구현이 필요
//     구글 OAuth 의 경우 Firebase 다른 서비스도 같이 구현하는 것을 추천
//     SNS 로그인의 경우 요청을 보내서 회원가입이 되어있다면 로그인,
//     회원가입되지 않았단 응답이 오면 곧바로 회원가입 절차에 들어가기.
//     비밀번호 찾기 버튼의 경우는 이메일 입력 화면이 나오고, 이메일 입력하면 해당 이메일에 초기화 버튼이 있어서 누르면 초기화된 비밀번호가 반환됨
class ActivityUserLoginSample : AppCompatActivity() {
    // <설정 변수 공간>
    // (앱 진입 필수 권한 배열)
    // : 앱 진입에 필요한 권한 배열.
    //     ex : Manifest.permission.INTERNET
    private val activityPermissionArrayMbr: Array<String> = arrayOf()


    // ---------------------------------------------------------------------------------------------
    // <멤버 변수 공간>
    // (뷰 바인더 객체)
    lateinit var bindingMbr: ActivityUserLoginSampleBinding

    // (repository 모델)
    lateinit var repositorySetMbr: RepositorySet

    // (어뎁터 객체)
    lateinit var adapterSetMbr: ActivityUserLoginSampleAdapterSet

    // (SharedPreference 객체)
    // 클래스 비휘발성 저장객체
    lateinit var classSpwMbr: ActivityUserLoginSampleSpw

    // 현 로그인 정보 접근 객체
    lateinit var currentLoginSessionInfoSpwMbr: CurrentLoginSessionInfoSpw

    // (스레드 풀)
    val executorServiceMbr: ExecutorService = Executors.newCachedThreadPool()

    // (다이얼로그 객체)
    var dialogMbr: Dialog? = null
    var shownDialogInfoVOMbr: InterfaceDialogInfoVO? = null
        set(value) {
            when (value) {
                is DialogBinaryChoose.DialogInfoVO -> {
                    dialogMbr?.dismiss()

                    dialogMbr = DialogBinaryChoose(
                        this,
                        value
                    )
                    dialogMbr?.show()
                }
                is DialogConfirm.DialogInfoVO -> {
                    dialogMbr?.dismiss()

                    dialogMbr = DialogConfirm(
                        this,
                        value
                    )
                    dialogMbr?.show()
                }
                is DialogProgressLoading.DialogInfoVO -> {
                    dialogMbr?.dismiss()

                    dialogMbr = DialogProgressLoading(
                        this,
                        value
                    )
                    dialogMbr?.show()
                }
                is DialogRadioButtonChoose.DialogInfoVO -> {
                    dialogMbr?.dismiss()

                    dialogMbr = DialogRadioButtonChoose(
                        this,
                        value
                    )
                    dialogMbr?.show()
                }
                else -> {
                    dialogMbr?.dismiss()
                    dialogMbr = null

                    field = null
                    return
                }
            }
            field = value
        }

    // (권한 요청 객체)
    lateinit var permissionRequestMbr: ActivityResultLauncher<Array<String>>
    var permissionRequestCallbackMbr: (((Map<String, Boolean>) -> Unit))? = null
    var permissionRequestOnProgressMbr = false
    val permissionRequestOnProgressSemaphoreMbr = Semaphore(1)

    // (ActivityResultLauncher 객체)
    // : 액티비티 결과 받아오기 객체. 사용법은 permissionRequestMbr 와 동일
    lateinit var resultLauncherMbr: ActivityResultLauncher<Intent>
    var resultLauncherCallbackMbr: ((ActivityResult) -> Unit)? = null

    // 구글 로그인 객체
    lateinit var googleSignInClient: GoogleSignInClient


    // ---------------------------------------------------------------------------------------------
    // <클래스 생명주기 공간>
    // : 액티비티 실행  = onCreate() → onStart() → onResume()
    //     액티비티 일시정지 후 재실행 = onPause() → ... -> onResume()
    //     액티비티 정지 후 재실행 = onPause() → onStop() -> ... -> onStart() → onResume()
    //     액티비티 종료 = onPause() → onStop() → onDestroy()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // (초기 객체 생성)
        onCreateInitObject()

        // (초기 뷰 설정)
        onCreateInitView()
    }

    override fun onResume() {
        super.onResume()

        // (권한 체크 후 함수 실행)
        // : requestPermission 시에 onPause 되고, onResume 이 다시 실행되므로 리퀘스트 복귀 시엔 여기를 지나게 되어있음
        var isPermissionAllGranted = true
        for (activityPermission in activityPermissionArrayMbr) {
            if (checkSelfPermission(activityPermission)
                == PackageManager.PERMISSION_DENIED
            ) { // 거부된 필수 권한이 존재
                // 권한 클리어 플래그를 변경하고 break
                isPermissionAllGranted = false
                break
            }
        }

        if (isPermissionAllGranted) { // 모든 권한이 클리어된 상황
            allPermissionsGranted()
            return
        }

        // (권한 비충족으로 인한 권한 요청)
        // : 권한 요청시엔 onPause 되었다가 다시 onResume 으로 복귀함
        executorServiceMbr.execute {
            permissionRequestOnProgressSemaphoreMbr.acquire()
            runOnUiThread {
                if (!permissionRequestOnProgressMbr) { // 현재 권한 요청중이 아님
                    permissionRequestOnProgressMbr = true
                    permissionRequestOnProgressSemaphoreMbr.release()
                    // (액티비티 진입 필수 권한 확인)
                    // 진입 필수 권한이 클리어 되어야 로직이 실행

                    // 권한 요청 콜백
                    permissionRequestCallbackMbr = { permissions ->
                        var isPermissionAllGranted1 = true
                        var neverAskAgain = false
                        for (activityPermission in activityPermissionArrayMbr) {
                            if (!permissions[activityPermission]!!) { // 거부된 필수 권한이 존재
                                // 권한 클리어 플래그를 변경하고 break
                                neverAskAgain =
                                    !shouldShowRequestPermissionRationale(activityPermission)
                                isPermissionAllGranted1 = false
                                break
                            }
                        }

                        if (isPermissionAllGranted1) { // 모든 권한이 클리어된 상황
                            permissionRequestOnProgressSemaphoreMbr.acquire()
                            permissionRequestOnProgressMbr = false
                            permissionRequestOnProgressSemaphoreMbr.release()

                        } else if (!neverAskAgain) { // 단순 거부
                            shownDialogInfoVOMbr = DialogConfirm.DialogInfoVO(
                                true,
                                "권한 필요",
                                "서비스를 실행하기 위한 필수 권한이 거부되었습니다.",
                                "뒤로가기",
                                onCheckBtnClicked = {
                                    shownDialogInfoVOMbr = null
                                    permissionRequestOnProgressSemaphoreMbr.acquire()
                                    permissionRequestOnProgressMbr = false
                                    permissionRequestOnProgressSemaphoreMbr.release()

                                    finish()
                                },
                                onCanceled = {
                                    shownDialogInfoVOMbr = null
                                    permissionRequestOnProgressSemaphoreMbr.acquire()
                                    permissionRequestOnProgressMbr = false
                                    permissionRequestOnProgressSemaphoreMbr.release()

                                    finish()
                                }
                            )

                        } else { // 권한 클리어 되지 않음 + 다시 묻지 않기 선택
                            shownDialogInfoVOMbr =
                                DialogBinaryChoose.DialogInfoVO(
                                    false,
                                    "권한 요청",
                                    "해당 서비스를 이용하기 위해선\n" +
                                            "필수 권한 승인이 필요합니다.\n" +
                                            "권한 설정 화면으로 이동하시겠습니까?",
                                    null,
                                    null,
                                    onPosBtnClicked = {
                                        shownDialogInfoVOMbr = null

                                        // 권한 설정 화면으로 이동
                                        val intent =
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                        intent.data = Uri.fromParts("package", packageName, null)

                                        resultLauncherCallbackMbr = {
                                            // 설정 페이지 복귀시 콜백
                                            var isPermissionAllGranted2 = true
                                            for (activityPermission in activityPermissionArrayMbr) {
                                                if (ActivityCompat.checkSelfPermission(
                                                        this,
                                                        activityPermission
                                                    ) != PackageManager.PERMISSION_GRANTED
                                                ) { // 거부된 필수 권한이 존재
                                                    // 권한 클리어 플래그를 변경하고 break
                                                    isPermissionAllGranted2 = false
                                                    break
                                                }
                                            }

                                            if (isPermissionAllGranted2) { // 권한 승인
                                                permissionRequestOnProgressSemaphoreMbr.acquire()
                                                permissionRequestOnProgressMbr = false
                                                permissionRequestOnProgressSemaphoreMbr.release()

                                            } else { // 권한 거부
                                                shownDialogInfoVOMbr =
                                                    DialogConfirm.DialogInfoVO(
                                                        true,
                                                        "권한 요청",
                                                        "서비스를 실행하기 위한 필수 권한이 거부되었습니다.",
                                                        "뒤로가기",
                                                        onCheckBtnClicked = {
                                                            shownDialogInfoVOMbr =
                                                                null
                                                            permissionRequestOnProgressSemaphoreMbr.acquire()
                                                            permissionRequestOnProgressMbr = false
                                                            permissionRequestOnProgressSemaphoreMbr.release()

                                                            finish()
                                                        },
                                                        onCanceled = {
                                                            shownDialogInfoVOMbr =
                                                                null
                                                            permissionRequestOnProgressSemaphoreMbr.acquire()
                                                            permissionRequestOnProgressMbr = false
                                                            permissionRequestOnProgressSemaphoreMbr.release()

                                                            finish()
                                                        }
                                                    )
                                            }
                                        }
                                        resultLauncherMbr.launch(intent)
                                    },
                                    onNegBtnClicked = {
                                        shownDialogInfoVOMbr = null

                                        shownDialogInfoVOMbr =
                                            DialogConfirm.DialogInfoVO(
                                                true,
                                                "권한 요청",
                                                "서비스를 실행하기 위한 필수 권한이 거부되었습니다.",
                                                "뒤로가기",
                                                onCheckBtnClicked = {
                                                    shownDialogInfoVOMbr =
                                                        null
                                                    permissionRequestOnProgressSemaphoreMbr.acquire()
                                                    permissionRequestOnProgressMbr = false
                                                    permissionRequestOnProgressSemaphoreMbr.release()

                                                    finish()
                                                },
                                                onCanceled = {
                                                    shownDialogInfoVOMbr =
                                                        null
                                                    permissionRequestOnProgressSemaphoreMbr.acquire()
                                                    permissionRequestOnProgressMbr = false
                                                    permissionRequestOnProgressSemaphoreMbr.release()

                                                    finish()
                                                }
                                            )
                                    },
                                    onCanceled = {
                                        shownDialogInfoVOMbr = null

                                        shownDialogInfoVOMbr =
                                            DialogConfirm.DialogInfoVO(
                                                true,
                                                "권한 요청",
                                                "서비스를 실행하기 위한 필수 권한이 거부되었습니다.",
                                                "뒤로가기",
                                                onCheckBtnClicked = {
                                                    shownDialogInfoVOMbr =
                                                        null
                                                    permissionRequestOnProgressSemaphoreMbr.acquire()
                                                    permissionRequestOnProgressMbr = false
                                                    permissionRequestOnProgressSemaphoreMbr.release()

                                                    finish()
                                                },
                                                onCanceled = {
                                                    shownDialogInfoVOMbr =
                                                        null
                                                    permissionRequestOnProgressSemaphoreMbr.acquire()
                                                    permissionRequestOnProgressMbr = false
                                                    permissionRequestOnProgressSemaphoreMbr.release()

                                                    finish()
                                                }
                                            )
                                    }
                                )

                        }
                    }

                    // 권한 요청
                    permissionRequestMbr.launch(activityPermissionArrayMbr)
                } else { // 현재 권한 요청중
                    permissionRequestOnProgressSemaphoreMbr.release()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        permissionRequestOnProgressSemaphoreMbr.acquire()
        if (permissionRequestOnProgressMbr) {
            permissionRequestOnProgressSemaphoreMbr.release()
            // 권한 요청중엔 onPause 가 실행될 수 있기에 아래에 위치할 정상 pause 로직 도달 방지
            return
        }
        permissionRequestOnProgressSemaphoreMbr.release()

        // (onPause 알고리즘)
    }

    override fun onDestroy() {
        super.onDestroy()

        // 다이얼로그 객체 해소
        dialogMbr?.dismiss()
    }

    // (AndroidManifest.xml 에서 configChanges 에 설정된 요소에 변경 사항이 존재할 때 실행되는 콜백)
    // : 해당 이벤트 발생시 처리할 로직을 작성
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) { // 화면회전 landscape

        } else if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) { // 화면회전 portrait

        }
    }

    // 키보드 바깥을 누르면 키보드를 숨김
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val focusView: View? = currentFocus
        if (focusView != null) {
            val rect = Rect()
            focusView.getGlobalVisibleRect(rect)
            val x = ev.x.toInt()
            val y = ev.y.toInt()
            if (!rect.contains(x, y)) {
                val imm: InputMethodManager =
                    getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(focusView.windowToken, 0)
                focusView.clearFocus()
            }
        }
        return super.dispatchTouchEvent(ev)
    }


    // ---------------------------------------------------------------------------------------------
    // <공개 메소드 공간>


    // ---------------------------------------------------------------------------------------------
    // <비공개 메소드 공간>
    // (초기 객체 생성)
    // : 클래스에서 사용할 객체를 초기 생성
    private fun onCreateInitObject() {
        // 뷰 객체
        bindingMbr = ActivityUserLoginSampleBinding.inflate(layoutInflater)
        // 뷰 객체 바인딩
        setContentView(bindingMbr.root)

        // 레포지토리 객체
        repositorySetMbr = RepositorySet.getInstance(application)

        // 어뎁터 셋 객체 생성 (어뎁터 내부 데이터가 포함된 객체)
        adapterSetMbr = ActivityUserLoginSampleAdapterSet()

        // SPW 객체 생성
        classSpwMbr = ActivityUserLoginSampleSpw(application)
        currentLoginSessionInfoSpwMbr = CurrentLoginSessionInfoSpw(application)

        // 권한 요청 객체 생성
        permissionRequestMbr =
            registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) {
                permissionRequestCallbackMbr?.let { it1 -> it1(it) }
            }

        // ActivityResultLauncher 생성
        resultLauncherMbr = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            resultLauncherCallbackMbr?.let { it1 -> it1(it) }
        }

        googleSignInClient = GoogleSignIn.getClient(
            this,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                // firebase 콘솔 - 안드로이드 해당 프로젝트 - Authentication - sign-in-method - google 로그인 안의 웹 클라이언트 아이디
                .requestIdToken("858569574580-9buovt0tteuavt71o2jdvuee6d19o2qp.apps.googleusercontent.com")
                .requestEmail()
                .build()
        )
    }

    // (초기 뷰 설정)
    // : 뷰 리스너 바인딩, 초기 뷰 사이즈, 위치 조정 등
    private fun onCreateInitView() {
        bindingMbr.goToJoinBtn.setOnClickListener {
            val intent =
                Intent(
                    this,
                    ActivityEmailUserJoinSample::class.java
                )
            startActivity(intent)
        }

        // (이메일 로그인 버튼 처리 관련)
        // : SNS 로그인은 따로 버튼 처리 필요

        // 로그인 버튼 비활성화
        //     적합성 검증 완료시 활성
        bindingMbr.emailLoginBtn.isEnabled = false
        bindingMbr.emailLoginBtn.isFocusable = false


        // (적합성 검증)
        var emailClear = false
        var pwClear = false

        bindingMbr.emailTextInputEditTxt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                bindingMbr.emailTextInputLayout.error = null
                bindingMbr.emailTextInputLayout.isErrorEnabled = false
            }

            override fun afterTextChanged(s: Editable) {
                val email = s.toString()

                when {
                    // 문자 입력 전
                    "" == email -> {
                        bindingMbr.emailTextInputLayout.error = null
                        bindingMbr.emailTextInputLayout.isErrorEnabled = false
                        emailClear = false

                        bindingMbr.emailLoginBtn.isEnabled = false
                        bindingMbr.emailLoginBtn.isFocusable = false
                    }

                    // 공백 존재
                    email.contains(" ") -> {
                        bindingMbr.emailTextInputLayout.error = "공백 문자는 입력할 수 없습니다."
                        emailClear = false

                        bindingMbr.emailLoginBtn.isEnabled = false
                        bindingMbr.emailLoginBtn.isFocusable = false
                    }

                    !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                        bindingMbr.emailTextInputLayout.error = "이메일 형태가 아닙니다."
                        emailClear = false

                        bindingMbr.emailLoginBtn.isEnabled = false
                        bindingMbr.emailLoginBtn.isFocusable = false
                    }

                    else -> {
                        bindingMbr.emailTextInputLayout.error = null
                        bindingMbr.emailTextInputLayout.isErrorEnabled = false

                        emailClear = true
                        if (emailClear && pwClear) {
                            bindingMbr.emailLoginBtn.isEnabled = true
                            bindingMbr.emailLoginBtn.isFocusable = true
                        }
                    }
                }
            }
        })

        bindingMbr.pwTextInputEditTxt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                bindingMbr.pwTextInputLayout.error = null
                bindingMbr.pwTextInputLayout.isErrorEnabled = false
            }

            override fun afterTextChanged(s: Editable) {
                val pw = s.toString()

                when {
                    "" == pw -> {
                        bindingMbr.pwTextInputLayout.error = null
                        bindingMbr.pwTextInputLayout.isErrorEnabled = false
                        pwClear = false

                        bindingMbr.emailLoginBtn.isEnabled = false
                        bindingMbr.emailLoginBtn.isFocusable = false
                    }

                    // 공백 존재
                    pw.contains(" ") -> {
                        bindingMbr.pwTextInputLayout.error = "공백 문자가 들어갔습니다."
                        pwClear = false

                        bindingMbr.emailLoginBtn.isEnabled = false
                        bindingMbr.emailLoginBtn.isFocusable = false
                    }

                    else -> {
                        bindingMbr.pwTextInputLayout.error = null
                        bindingMbr.pwTextInputLayout.isErrorEnabled = false
                        pwClear = true

                        if (emailClear && pwClear) {
                            bindingMbr.emailLoginBtn.isEnabled = true
                            bindingMbr.emailLoginBtn.isFocusable = true
                        }
                    }
                }
            }
        })

        bindingMbr.emailLoginBtn.setOnClickListener {
            // 적합성 검증 완료를 가정
            if (!emailClear || !pwClear) {
                return@setOnClickListener
            }

            // 로그인 정보 가져오기
            val email: String = bindingMbr.emailTextInputEditTxt.text.toString()
            val pw: String = bindingMbr.pwTextInputEditTxt.text.toString()

            shownDialogInfoVOMbr = DialogProgressLoading.DialogInfoVO(
                false,
                "로그인 중입니다.",
                onCanceled = {}
            )

            // 로그인 요청
            // (정보 요청 콜백)
            val loginCompleteCallback =
                { statusCode: Int,
                  userUid: Long?, // 클라이언트 내 현 유저 구분을 위한 고유 값
                  userNickName: String?, // 닉네임은 다른 디바이스에서 변경이 가능하니 받아오기
                  accessToken: String?,
                  accessTokenExpireDate: String?,
                  refreshToken: String?,
                  refreshTokenExpireDate: String? ->
                    runOnUiThread {
                        when (statusCode) {
                            -1 -> { // 네트워크 에러
                                shownDialogInfoVOMbr = DialogConfirm.DialogInfoVO(
                                    true,
                                    "네트워크 불안정",
                                    "현재 네트워크 연결이 불안정합니다.",
                                    null,
                                    onCheckBtnClicked = {
                                        shownDialogInfoVOMbr = null
                                    },
                                    onCanceled = {
                                        shownDialogInfoVOMbr = null
                                    }
                                )
                            }
                            1 -> {// 로그인 완료
                                // 회원 처리
                                currentLoginSessionInfoSpwMbr.setLocalDataLogin(
                                    currentLoginSessionInfoSpwMbr.isAutoLogin,
                                    1,
                                    email,
                                    pw,
                                    userUid!!.toString(),
                                    userNickName!!,
                                    accessToken,
                                    accessTokenExpireDate,
                                    refreshToken,
                                    refreshTokenExpireDate
                                )

                                // 로그인 검증됨
                                currentLoginSessionInfoSpwMbr.isAutoLogin = true

                                shownDialogInfoVOMbr = DialogConfirm.DialogInfoVO(
                                    true,
                                    "로그인 완료",
                                    "로그인 되었습니다.",
                                    "닫기",
                                    onCheckBtnClicked = {
                                        shownDialogInfoVOMbr = null
                                        finish()
                                    },
                                    onCanceled = {
                                        shownDialogInfoVOMbr = null
                                        finish()
                                    }
                                )
                            }
                            2 -> { // 입력값 에러 = 일어나면 안되는 에러
                                throw java.lang.Exception("login input error")
                            }
                            3 -> { // 가입된 회원이 아님
                                shownDialogInfoVOMbr = DialogConfirm.DialogInfoVO(
                                    true,
                                    "로그인 실패",
                                    "가입되지 않은 회원입니다.",
                                    null,
                                    onCheckBtnClicked = {
                                        shownDialogInfoVOMbr = null
                                    },
                                    onCanceled = {
                                        shownDialogInfoVOMbr = null
                                    }
                                )
                            }
                            4 -> { // 로그인 정보 불일치
                                shownDialogInfoVOMbr = DialogConfirm.DialogInfoVO(
                                    true,
                                    "로그인 실패",
                                    "로그인 정보가 일치하지 않습니다.",
                                    null,
                                    onCheckBtnClicked = {
                                        shownDialogInfoVOMbr = null
                                    },
                                    onCanceled = {
                                        shownDialogInfoVOMbr = null
                                    }
                                )
                            }
                            else -> { // 그외 서버 에러
                                shownDialogInfoVOMbr = DialogConfirm.DialogInfoVO(
                                    true,
                                    "기술적 문제",
                                    "기술적 문제가 발생했습니다.\n잠시후 다시 시도해주세요.",
                                    null,
                                    onCheckBtnClicked = {
                                        shownDialogInfoVOMbr = null
                                    },
                                    onCanceled = {
                                        shownDialogInfoVOMbr = null
                                    }
                                )
                            }
                        }
                    }
                }

            // (로그인 요청)
            // 서버에선 보내준 id, pw 를 가지고 적절한 검증 과정을 거치고 정보 반환
            executorServiceMbr.execute {
                // 아래는 원래 네트워크 서버에서 처리하는 로직
                val userInfoList =
                    repositorySetMbr.databaseRoomMbr.appDatabaseMbr.testUserInfoTableDao()
                        .getUserInfoForLogin(
                            email,
                            1
                        )

                if (userInfoList.isEmpty()) { // 일치하는 정보가 없음
                    loginCompleteCallback(
                        3,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                    )
                } else {
                    val uid = userInfoList[0].uid
                    val nickname = userInfoList[0].nickName
                    val password = userInfoList[0].password

                    if (password != pw
                    ) { // 이메일 로그인 비밀번호 불일치
                        loginCompleteCallback(
                            4,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null
                        )
                    } else { // 검증 완료
                        // jwt 사용시 access token, refresh token 발행
                        // 여기선 jwt 를 구현하지 않았기에 null 반환
                        loginCompleteCallback(
                            1,
                            uid,
                            nickname,
                            null,
                            null,
                            null,
                            null
                        )
                    }
                }
            }
        }

        bindingMbr.googleLoginBtn.setOnClickListener {
            // 구글 로그인을 하려면
            // 1. firebase 콘솔에서 프로젝트를 등록하고 SHA1 을 등록한 후 얻은 google-services.json 파일을
            //     프로젝트 앱 디렉토리에 붙여넣기
            // 2. firebase 콘솔의 해당 프로젝트에서 Authentication - sign-in-method 에서 구글 로그인을 추가

            resultLauncherCallbackMbr = {
                if (it.resultCode == Activity.RESULT_OK) {
                    val result = Auth.GoogleSignInApi.getSignInResultFromIntent(it.data!!)!!

                    if (result.isSuccess) {
                        val account = result.signInAccount!!
                        val snsId = account.id!!
                        val idToken = account.idToken!!
                        val name = account.displayName!!
//                        val email = account.email
//                        val image = account.photoUrl.toString()
//                        val credential = GoogleAuthProvider.getCredential(idToken, null)

                        shownDialogInfoVOMbr = DialogProgressLoading.DialogInfoVO(
                            false,
                            "로그인 중입니다.",
                            onCanceled = {}
                        )

                        // 로그인 요청
                        // (정보 요청 콜백)
                        val loginCompleteCallback =
                            { statusCode: Int,
                              userUid: Long?, // 클라이언트 내 현 유저 구분을 위한 고유 값
                              userNickName: String?, // 닉네임은 다른 디바이스에서 변경이 가능하니 받아오기
                              accessToken: String?,
                              accessTokenExpireDate: String?,
                              refreshToken: String?,
                              refreshTokenExpireDate: String? ->
                                runOnUiThread {
                                    when (statusCode) {
                                        -1 -> { // 네트워크 에러
                                            shownDialogInfoVOMbr = DialogConfirm.DialogInfoVO(
                                                true,
                                                "네트워크 불안정",
                                                "현재 네트워크 연결이 불안정합니다.",
                                                null,
                                                onCheckBtnClicked = {
                                                    shownDialogInfoVOMbr = null
                                                },
                                                onCanceled = {
                                                    shownDialogInfoVOMbr = null
                                                }
                                            )
                                        }
                                        1 -> {// 로그인 완료
                                            // 회원 처리
                                            currentLoginSessionInfoSpwMbr.setLocalDataLogin(
                                                currentLoginSessionInfoSpwMbr.isAutoLogin,
                                                2,
                                                snsId,
                                                idToken,
                                                userUid!!.toString(),
                                                userNickName!!,
                                                accessToken,
                                                accessTokenExpireDate,
                                                refreshToken,
                                                refreshTokenExpireDate
                                            )

                                            // 로그인 검증됨
                                            currentLoginSessionInfoSpwMbr.isAutoLogin = true

                                            shownDialogInfoVOMbr = DialogConfirm.DialogInfoVO(
                                                true,
                                                "로그인 완료",
                                                "로그인 되었습니다.",
                                                "닫기",
                                                onCheckBtnClicked = {
                                                    shownDialogInfoVOMbr = null
                                                    finish()
                                                },
                                                onCanceled = {
                                                    shownDialogInfoVOMbr = null
                                                    finish()
                                                }
                                            )
                                        }
                                        2 -> { // 입력값 에러 = 일어나면 안되는 에러
                                            throw java.lang.Exception("login input error")
                                        }
                                        3 -> { // 가입된 회원이 아님
                                            shownDialogInfoVOMbr =
                                                DialogProgressLoading.DialogInfoVO(
                                                    false,
                                                    "",
                                                    onCanceled = {}
                                                )

                                            // 회원가입
                                            // 회원가입 콜백
                                            val signInCallback = { statusCode: Int ->
                                                runOnUiThread {
                                                    when (statusCode) {
                                                        -1 -> { // 네트워크 에러
                                                            shownDialogInfoVOMbr =
                                                                DialogConfirm.DialogInfoVO(
                                                                    true,
                                                                    "네트워크 불안정",
                                                                    "현재 네트워크 연결이 불안정합니다.",
                                                                    null,
                                                                    onCheckBtnClicked = {
                                                                        shownDialogInfoVOMbr = null
                                                                    },
                                                                    onCanceled = {
                                                                        shownDialogInfoVOMbr = null
                                                                    }
                                                                )
                                                        }
                                                        1, 3 -> { // 회원 가입 완료 or 이미 가입된 회원(드물긴 하지만 다른 디바이스에서 로그인 완료됨)
                                                            // 로그인

                                                            // (정보 요청 콜백)
                                                            val loginCompleteCallback =
                                                                { statusCode: Int,
                                                                  userUid: Long?, // 클라이언트 내 현 유저 구분을 위한 고유 값
                                                                  userNickName: String?, // 닉네임은 다른 디바이스에서 변경이 가능하니 받아오기
                                                                  accessToken: String?,
                                                                  accessTokenExpireDate: String?,
                                                                  refreshToken: String?,
                                                                  refreshTokenExpireDate: String? ->
                                                                    runOnUiThread {
                                                                        when (statusCode) {
                                                                            -1 -> { // 네트워크 에러
                                                                                shownDialogInfoVOMbr =
                                                                                    DialogConfirm.DialogInfoVO(
                                                                                        true,
                                                                                        "네트워크 불안정",
                                                                                        "현재 네트워크 연결이 불안정합니다.",
                                                                                        null,
                                                                                        onCheckBtnClicked = {
                                                                                            shownDialogInfoVOMbr =
                                                                                                null
                                                                                        },
                                                                                        onCanceled = {
                                                                                            shownDialogInfoVOMbr =
                                                                                                null
                                                                                        }
                                                                                    )
                                                                            }
                                                                            1 -> {// 로그인 완료
                                                                                // 회원 처리
                                                                                currentLoginSessionInfoSpwMbr.setLocalDataLogin(
                                                                                    currentLoginSessionInfoSpwMbr.isAutoLogin,
                                                                                    2,
                                                                                    snsId,
                                                                                    idToken,
                                                                                    userUid!!.toString(),
                                                                                    userNickName!!,
                                                                                    accessToken,
                                                                                    accessTokenExpireDate,
                                                                                    refreshToken,
                                                                                    refreshTokenExpireDate
                                                                                )

                                                                                // 로그인 검증됨
                                                                                currentLoginSessionInfoSpwMbr.isAutoLogin =
                                                                                    true

                                                                                shownDialogInfoVOMbr =
                                                                                    DialogConfirm.DialogInfoVO(
                                                                                        true,
                                                                                        "로그인 완료",
                                                                                        "로그인 되었습니다.",
                                                                                        "닫기",
                                                                                        onCheckBtnClicked = {
                                                                                            shownDialogInfoVOMbr =
                                                                                                null
                                                                                            finish()
                                                                                        },
                                                                                        onCanceled = {
                                                                                            shownDialogInfoVOMbr =
                                                                                                null
                                                                                            finish()
                                                                                        }
                                                                                    )
                                                                            }
                                                                            2 -> { // 입력값 에러 = 일어나면 안되는 에러
                                                                                throw java.lang.Exception(
                                                                                    "login input error"
                                                                                )
                                                                            }
                                                                            3 -> { // 가입된 회원이 아님
                                                                                // 가능성이 아예 없는 것은 아닌데 인간적으로 발생하는 사건이 아니라고 판단 가능함.
                                                                                // = 회원이 없어서 회원가입을 한 바로 그 시점에 회원 탈퇴가 완료된 상황
                                                                                shownDialogInfoVOMbr =
                                                                                    DialogConfirm.DialogInfoVO(
                                                                                        true,
                                                                                        "기술적 문제",
                                                                                        "기술적 문제가 발생했습니다.\n잠시후 다시 시도해주세요.",
                                                                                        null,
                                                                                        onCheckBtnClicked = {
                                                                                            shownDialogInfoVOMbr =
                                                                                                null
                                                                                        },
                                                                                        onCanceled = {
                                                                                            shownDialogInfoVOMbr =
                                                                                                null
                                                                                        }
                                                                                    )
                                                                            }
                                                                            4 -> { // 로그인 정보 불일치
                                                                                shownDialogInfoVOMbr =
                                                                                    DialogConfirm.DialogInfoVO(
                                                                                        true,
                                                                                        "로그인 실패",
                                                                                        "로그인 정보가 일치하지 않습니다.",
                                                                                        null,
                                                                                        onCheckBtnClicked = {
                                                                                            shownDialogInfoVOMbr =
                                                                                                null
                                                                                        },
                                                                                        onCanceled = {
                                                                                            shownDialogInfoVOMbr =
                                                                                                null
                                                                                        }
                                                                                    )
                                                                            }
                                                                            else -> { // 그외 서버 에러
                                                                                shownDialogInfoVOMbr =
                                                                                    DialogConfirm.DialogInfoVO(
                                                                                        true,
                                                                                        "기술적 문제",
                                                                                        "기술적 문제가 발생했습니다.\n잠시후 다시 시도해주세요.",
                                                                                        null,
                                                                                        onCheckBtnClicked = {
                                                                                            shownDialogInfoVOMbr =
                                                                                                null
                                                                                        },
                                                                                        onCanceled = {
                                                                                            shownDialogInfoVOMbr =
                                                                                                null
                                                                                        }
                                                                                    )
                                                                            }
                                                                        }
                                                                    }
                                                                }

                                                            // (로그인 요청)
                                                            // 서버에선 보내준 id, pw 를 가지고 적절한 검증 과정을 거치고 정보 반환
                                                            executorServiceMbr.execute {
                                                                // 아래는 원래 네트워크 서버에서 처리하는 로직
                                                                val userInfoList =
                                                                    repositorySetMbr.databaseRoomMbr.appDatabaseMbr.testUserInfoTableDao()
                                                                        .getUserInfoForLogin(
                                                                            snsId,
                                                                            2
                                                                        )

                                                                if (userInfoList.isEmpty()) { // 일치하는 정보가 없음
                                                                    loginCompleteCallback(
                                                                        3,
                                                                        null,
                                                                        null,
                                                                        null,
                                                                        null,
                                                                        null,
                                                                        null
                                                                    )
                                                                } else {
                                                                    val uid = userInfoList[0].uid
                                                                    val nickname =
                                                                        userInfoList[0].nickName
                                                                    val password =
                                                                        userInfoList[0].password

                                                                    // jwt 사용시 access token, refresh token 발행
                                                                    // 여기선 jwt 를 구현하지 않았기에 null 반환
                                                                    loginCompleteCallback(
                                                                        1,
                                                                        uid,
                                                                        nickname,
                                                                        null,
                                                                        null,
                                                                        null,
                                                                        null
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        2 -> { // 입력값 에러
                                                            throw Exception("user join input error")
                                                        }
                                                        else -> { // 그외 서버 에러
                                                            shownDialogInfoVOMbr =
                                                                DialogConfirm.DialogInfoVO(
                                                                    true,
                                                                    "기술적 문제",
                                                                    "기술적 문제가 발생했습니다.\n잠시후 다시 시도해주세요.",
                                                                    null,
                                                                    onCheckBtnClicked = {
                                                                        shownDialogInfoVOMbr = null
                                                                    },
                                                                    onCanceled = {
                                                                        shownDialogInfoVOMbr = null
                                                                    }
                                                                )
                                                        }
                                                    }
                                                }
                                            }

                                            // 회원가입 요청
                                            executorServiceMbr.execute {
                                                // 아래는 원래 네트워크 서버에서 처리하는 로직
                                                // 이메일 중복검사
                                                val emailCount =
                                                    repositorySetMbr.databaseRoomMbr.appDatabaseMbr.testUserInfoTableDao()
                                                        .getIdCount(snsId, 2)

                                                if (emailCount != 0) { // 아이디 중복
                                                    signInCallback(3)
                                                } else {
                                                    repositorySetMbr.databaseRoomMbr.appDatabaseMbr.testUserInfoTableDao()
                                                        .insert(
                                                            TestUserInfoTable.TableVo(
                                                                2,
                                                                snsId,
                                                                name,
                                                                idToken
                                                            )
                                                        )
                                                    signInCallback(1)
                                                }
                                            }
                                        }
                                        4 -> { // 로그인 정보 불일치
                                            shownDialogInfoVOMbr = DialogConfirm.DialogInfoVO(
                                                true,
                                                "로그인 실패",
                                                "로그인 정보가 일치하지 않습니다.",
                                                null,
                                                onCheckBtnClicked = {
                                                    shownDialogInfoVOMbr = null
                                                },
                                                onCanceled = {
                                                    shownDialogInfoVOMbr = null
                                                }
                                            )
                                        }
                                        else -> { // 그외 서버 에러
                                            shownDialogInfoVOMbr = DialogConfirm.DialogInfoVO(
                                                true,
                                                "기술적 문제",
                                                "기술적 문제가 발생했습니다.\n잠시후 다시 시도해주세요.",
                                                null,
                                                onCheckBtnClicked = {
                                                    shownDialogInfoVOMbr = null
                                                },
                                                onCanceled = {
                                                    shownDialogInfoVOMbr = null
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                        // (로그인 요청)
                        // 서버에선 보내준 id, pw 를 가지고 적절한 검증 과정을 거치고 정보 반환
                        executorServiceMbr.execute {
                            // 아래는 원래 네트워크 서버에서 처리하는 로직
                            val userInfoList =
                                repositorySetMbr.databaseRoomMbr.appDatabaseMbr.testUserInfoTableDao()
                                    .getUserInfoForLogin(
                                        snsId,
                                        2
                                    )

                            if (userInfoList.isEmpty()) { // 일치하는 정보가 없음
                                loginCompleteCallback(
                                    3,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null
                                )
                            } else {
                                val uid = userInfoList[0].uid
                                val nickname = userInfoList[0].nickName
                                val password = userInfoList[0].password

                                // jwt 사용시 access token, refresh token 발행
                                // 여기선 jwt 를 구현하지 않았기에 null 반환
                                loginCompleteCallback(
                                    1,
                                    uid,
                                    nickname,
                                    null,
                                    null,
                                    null,
                                    null
                                )
                            }
                        }

                    } else {
                        shownDialogInfoVOMbr = DialogConfirm.DialogInfoVO(
                            true,
                            "로그인 실패",
                            "SNS 로그인에 실패했습니다.\n" +
                                    "잠시후 다시 시도해주세요.",
                            null,
                            onCheckBtnClicked = {
                                shownDialogInfoVOMbr = null
                            },
                            onCanceled = {
                                shownDialogInfoVOMbr = null
                            }
                        )
                    }

                } else { // 로그인 취소 or 로그인 설정 에러

                }
            }

            resultLauncherMbr.launch(
                googleSignInClient.signInIntent
            )
        }

        // SNS 로그인의 경우는 SNS 로그인 후 액세스 토큰과 id 를 서버에 검증 보내고 로그인 처리 후 닫기
        // 만약 현재 회원이 아니라면 바로 회원가입 절차로 들어가고 로그인 처리 후 닫기
    }

    // (액티비티 진입 권한이 클리어 된 시점)
    // : 실질적인 액티비티 로직 실행구역
    private var doItAlreadyMbr = false
    private var currentUserUidMbr: String? = null // 유저 식별가능 정보 - null 이라면 비회원
    private fun allPermissionsGranted() {
        if (!doItAlreadyMbr) {
            // (권한이 충족된 onCreate)
            doItAlreadyMbr = true

            // (초기 데이터 수집)
            currentUserUidMbr = currentLoginSessionInfoSpwMbr.userUid
            refreshWholeScreenData(onComplete = {})

            // 로그인 된 상태라면 진입 금지
            if (currentUserUidMbr != null) {
                finish()
            }
        } else {
            // (onResume - (권한이 충족된 onCreate))

            // (유저별 데이터 갱신)
            // : 유저 정보가 갱신된 상태에서 다시 현 액티비티로 복귀하면 자동으로 데이터를 다시 갱신합니다.
            val userUid = currentLoginSessionInfoSpwMbr.userUid
            if (userUid != currentUserUidMbr) { // 액티비티 유저와 세션 유저가 다를 때
                // 진입 플래그 변경
                currentUserUidMbr = userUid

                // (데이터 수집)
                refreshWholeScreenData(onComplete = {})

                // 로그인 된 상태라면 진입 금지
                if (currentUserUidMbr != null) {
                    finish()
                }
            }

        }

        // (onResume)
    }

    // 화면 데이터 갱신관련 세마포어
    private val screenDataSemaphoreMbr = Semaphore(1)

    // (화면 구성용 데이터를 가져오기)
    // : 네트워크 등 레포지토리에서 데이터를 가져오고 이를 뷰에 반영
    //     onComplete = 네트워크 실패든 성공이든 데이터 요청 후 응답을 받아와 해당 상태에 따라 스크린 뷰 처리를 완료한 시점
    //     'c숫자' 로 표기된 부분은 원하는대로 커스텀
    private fun refreshWholeScreenData(onComplete: () -> Unit) {
        executorServiceMbr.execute {
            screenDataSemaphoreMbr.acquire()

            runOnUiThread {
                // (c1. 리스트 초기화)

                // (c2. 로더 추가)
            }

            // (스레드 합류 객체 생성)
            // : 헤더, 푸터, 아이템 리스트의 각 데이터를 비동기적으로 요청했을 때, 그 합류용으로 사용되는 객체
            //     numberOfThreadsBeingJoinedMbr 에 비동기 처리 개수를 적고,
            //     각 처리 완료시마다 threadComplete 를 호출하면 됨
            val threadConfluenceObj =
                ThreadConfluenceObj(
                    3,
                    onComplete = {
                        screenDataSemaphoreMbr.release()
                        onComplete()
                    }
                )

            // (정보 요청 콜백)
            // 아이템 리스트
            // : statusCode
            //     서버 반환 상태값. 1이라면 정상동작, -1 이라면 타임아웃, 2 이상 값들 중 서버에서 정한 상태값 처리, 그외엔 서버 에러
            //     1 이외의 상태값에서 itemList 는 null
            val getItemListOnComplete: (statusCode: Int, itemList: ArrayList<AbstractProwdRecyclerViewAdapter.AdapterItemAbstractVO>?) -> Unit =
                { statusCode, itemList ->
                    runOnUiThread {
                        // (c3. 로더 제거)
                    }

                    when (statusCode) {
                        1 -> { // 완료
                            if (itemList!!.isEmpty()) { // 받아온 리스트가 비어있을 때
                                // (c4. 빈 리스트 처리)

                                threadConfluenceObj.threadComplete()
                            } else {
                                runOnUiThread {
                                    // (c5. 받아온 아이템 추가)

                                    // (c6. 스크롤을 가장 앞으로 이동)
                                }

                                threadConfluenceObj.threadComplete()
                            }
                        }
                        -1 -> { // 네트워크 에러
                            // (c7. 네트워크 에러 처리)

                            threadConfluenceObj.threadComplete()
                        }
                        else -> { // 그외 서버 에러그외 서버 에러
                            // (c8. 그외 서버 에러 처리)

                            threadConfluenceObj.threadComplete()
                        }
                    }
                }

            // 헤더 아이템
            // : statusCode
            //     서버 반환 상태값. 1이라면 정상동작, -1 이라면 타임아웃, 2 이상 값들 중 서버에서 정한 상태값 처리, 그외엔 서버 에러
            //     1 이외의 상태값에서 item 은 null
            val getHeaderItemOnComplete: (statusCode: Int, item: AbstractProwdRecyclerViewAdapter.AdapterHeaderAbstractVO?) -> Unit =
                { statusCode, item ->
                    runOnUiThread {
                        // (c9. 로더 제거)
                    }

                    when (statusCode) {
                        1 -> { // 완료
                            runOnUiThread {
                                // (c10. 받아온 아이템 추가)
                            }

                            threadConfluenceObj.threadComplete()
                        }
                        -1 -> { // 네트워크 에러
                            // (c11. 네트워크 에러 처리)

                            threadConfluenceObj.threadComplete()
                        }
                        else -> { // 그외 서버 에러
                            // (c12. 그외 서버 에러 처리)

                            threadConfluenceObj.threadComplete()
                        }
                    }
                }

            // 푸터 아이템
            // : statusCode
            //     서버 반환 상태값. 1이라면 정상동작, -1 이라면 타임아웃, 2 이상 값들 중 서버에서 정한 상태값 처리, 그외엔 서버 에러
            //     1 이외의 상태값에서 item 은 null
            val getFooterItemOnComplete: (statusCode: Int, item: AbstractProwdRecyclerViewAdapter.AdapterFooterAbstractVO?) -> Unit =
                { statusCode, item ->
                    runOnUiThread {
                        // (c13. 로더 제거)
                    }

                    when (statusCode) {
                        1 -> {// 완료
                            runOnUiThread {
                                // (c14. 받아온 아이템 추가)
                            }

                            threadConfluenceObj.threadComplete()
                        }
                        -1 -> { // 네트워크 에러
                            // (c15. 네트워크 에러 처리)

                            threadConfluenceObj.threadComplete()
                        }
                        else -> { // 그외 서버 에러
                            // (c16. 그외 서버 에러 처리)

                            threadConfluenceObj.threadComplete()
                        }
                    }
                }

            // (네트워크 요청)
            // (c17. 아이템 리스트 가져오기)
            // : lastItemUid 등의 인자값을 네트워크 요청으로 넣어주고 데이터를 받아와서 onComplete 실행
            //     데이터 요청 API 는 정렬기준, 마지막 uid, 요청 아이템 개수 등을 입력하여 데이터 리스트를 반환받음
            executorServiceMbr.execute {
                getItemListOnComplete(-2, null)
            }

            // (c18. 헤더 데이터 가져오기)
            // : lastItemUid 등의 인자값을 네트워크 요청으로 넣어주고 데이터를 받아와서 onComplete 실행
            //     데이터 요청 API 는 정렬기준, 마지막 uid, 요청 아이템 개수 등을 입력하여 데이터 리스트를 반환받음
            executorServiceMbr.execute {
                getHeaderItemOnComplete(-2, null)
            }

            // (c19. 푸터 데이터 가져오기)
            // : lastItemUid 등의 인자값을 네트워크 요청으로 넣어주고 데이터를 받아와서 onComplete 실행
            //     데이터 요청 API 는 정렬기준, 마지막 uid, 요청 아이템 개수 등을 입력하여 데이터 리스트를 반환받음
            executorServiceMbr.execute {
                getFooterItemOnComplete(-2, null)
            }

            // (c20. 그외 스크린 데이터 가져오기)

        }
    }


    // ---------------------------------------------------------------------------------------------
    // <중첩 클래스 공간>
    // (클래스 비휘발 저장 객체)
    class ActivityUserLoginSampleSpw(application: Application) {
        // <멤버 변수 공간>
        // SharedPreference 접근 객체
        private val spMbr = application.getSharedPreferences(
            "ActivityUserLoginSampleSpw",
            Context.MODE_PRIVATE
        )

//        var testData: String?
//            get() {
//                return spMbr.getString(
//                    "testData",
//                    null
//                )
//            }
//            set(value) {
//                with(spMbr.edit()) {
//                    putString(
//                        "testData",
//                        value
//                    )
//                    apply()
//                }
//            }


        // ---------------------------------------------------------------------------------------------
        // <중첩 클래스 공간>

    }

    // (액티비티 내 사용 어뎁터 모음)
    // : 액티비티 내 사용할 어뎁터가 있다면 본문에 클래스 추가 후 인자로 해당 클래스의 인스턴스를 받도록 하기
    class ActivityUserLoginSampleAdapterSet {
        // 어뎁터 #1

    }
}