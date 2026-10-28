package com.liblink.app.ui

import android.content.Context
import com.liblink.app.R
import com.liblink.app.network.GitHubException

// رسالة مفهومة بدل خطأ GitHub الخام
fun ghError(ctx: Context, e: Exception): String = when {
    e is GitHubException && e.code == 401 -> ctx.getString(R.string.gh_err_401)
    e is GitHubException && e.code == 403 -> ctx.getString(R.string.gh_err_403)
    e is GitHubException && e.code == 404 -> ctx.getString(R.string.gh_err_404)
    e is GitHubException && e.code == 422 && (e.message ?: "").contains("already exists", true) -> ctx.getString(R.string.gh_err_exists)
    e is GitHubException -> ctx.getString(R.string.api_error, e.code, e.message ?: "")
    else -> ctx.getString(R.string.error_prefix, e.message ?: e.toString())
}
