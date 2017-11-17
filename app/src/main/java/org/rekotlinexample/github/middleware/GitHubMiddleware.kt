package org.rekotlinexample.github.middleware

import org.rekotlinexample.github.AppController
import org.rekotlinexample.github.BuildConfig
import org.rekotlinexample.github.actions.*
import org.rekotlinexample.github.apirequests.MockGitHubApiService
import org.rekotlinexample.github.asyntasks.RepoListTask
import org.rekotlinexample.github.asyntasks.UserLoginTask
import org.rekotlinexample.github.mainStore
import org.rekotlinexample.github.apirequests.PreferenceApiService
import org.rekotlinexample.github.apirequests.PreferenceApiService.GITHUB_PREFS_KEY_LOGINSTATUS
import org.rekotlinexample.github.apirequests.PreferenceApiService.GITHUB_PREFS_KEY_TOKEN
import org.rekotlinexample.github.apirequests.PreferenceApiService.GITHUB_PREFS_KEY_USERNAME
import org.rekotlinexample.github.states.GitHubAppState
import org.rekotlinexample.github.states.LoggedInState
import tw.geothings.rekotlin.*

/**
* Created by Mohanraj Karatadipalayam on 17/10/17.
*/


interface LoginTaskListenerInterface {
    fun onFinished(result: LoginCompletedAction)
}

class LoginTaskListenerMiddleware : LoginTaskListenerInterface {
    override fun onFinished(result: LoginCompletedAction){

        if (result.loginStatus == LoggedInState.loggedIn ) {
            result.token?.let {
                mainStore.dispatch(result)
                mainStore.dispatch(LoggedInDataSaveAction(userName = result.userName,
                        token = result.token as String, loginStatus = LoggedInState.loggedIn))
            }
        } else {
            result.message?.let{
                mainStore.dispatch(LoginFailedAction(userName = result.userName,
                        message = result.message as String))
            }

        }

    }
}

interface RepoListTaskListenerInterface {
    fun onFinished(result: RepoListCompletedAction)
}

class RepoListTaskListenerMiddleware: RepoListTaskListenerInterface {
    override fun onFinished(result: RepoListCompletedAction) {
        mainStore.dispatch(result)
    }

}

// App context for UT, must be never set in app run
var testAppContext = AppController.instance?.applicationContext

internal val gitHubMiddleware: Middleware<GitHubAppState> = { dispatch, getState ->
    { next ->
        { action ->
             when (action) {
                is LoginAction -> {
                    executeGitHubLogin(action, dispatch)
                }
                 is LoggedInDataSaveAction -> {
                     executeSaveLoginData(action)
                 }
                 is RepoDetailListAction -> {
                     executeGitHubRepoListRetrieval(action,dispatch)
                 }
             }

            next(action)

        }
    }
}

fun executeGitHubRepoListRetrieval(action: RepoDetailListAction,dispatch: DispatchFunction) : Boolean {

    var userName: String? = action.userName
    var token: String? = action.token
    val context = testAppContext ?: AppController.instance?.applicationContext
    context?.let {
        userName = PreferenceApiService.getPreference(context, GITHUB_PREFS_KEY_USERNAME)
        token = PreferenceApiService.getPreference(context, GITHUB_PREFS_KEY_TOKEN)
    }

    userName?.let {
        token?.let {
            val repoListTaskListenerMiddleware = RepoListTaskListenerMiddleware()
            val repoTask = RepoListTask(repoListTaskListenerMiddleware,
                    userName as String,
                    token as String)

            if(BuildConfig.ENABLE_MOCKS) {repoTask.githubService = MockGitHubApiService()}
            repoTask.execute()
            dispatch(RepoListRetrivalStartedAction())
            return true
        }
        return false
    }
    return false
}

private fun executeSaveLoginData(action: LoggedInDataSaveAction) {
    val context = testAppContext ?: AppController.instance?.applicationContext
    context?.let {
        PreferenceApiService.savePreference(context,
                GITHUB_PREFS_KEY_TOKEN, action.token)
        PreferenceApiService.savePreference(context,
                GITHUB_PREFS_KEY_USERNAME, action.userName)
        PreferenceApiService.savePreference(context,
                GITHUB_PREFS_KEY_LOGINSTATUS, LoggedInState.loggedIn.name)
    }
}

fun executeGitHubLogin(action: LoginAction, dispatch: DispatchFunction) {
    val loginTaskListenerMiddleware = LoginTaskListenerMiddleware()


    val authTask = UserLoginTask(loginTaskListenerMiddleware,
            action.userName,
            action.password )

    if(BuildConfig.ENABLE_MOCKS) { authTask.githubService = MockGitHubApiService() }

    authTask.execute()
    dispatch(LoginStartedAction(action.userName))
}




