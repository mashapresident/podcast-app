package com.podcastapp.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.podcastapp.data.repository.AuthRepository
import com.podcastapp.data.repository.Validators
import com.podcastapp.data.repository.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthState(
    val registerMode: Boolean = false,
    val email: String = "",
    val password: String = "",
    val name: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val nameError: String? = null,
    val serverError: String? = null,
    val loading: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(private val repo: AuthRepository) : ViewModel() {
    private val _s = MutableStateFlow(AuthState())
    val state = _s.asStateFlow()

    fun onEmail(v: String) = _s.update { it.copy(email = v, emailError = null, serverError = null) }
    fun onPassword(v: String) = _s.update { it.copy(password = v, passwordError = null, serverError = null) }
    fun onName(v: String) = _s.update { it.copy(name = v, nameError = null) }
    fun toggleMode() = _s.update { it.copy(registerMode = !it.registerMode, serverError = null) }

    fun submit(onSuccess: () -> Unit) {
        val s = _s.value
        val emailErr = if (Validators.isValidEmail(s.email)) null else "Некоректна пошта"
        val passErr = if (Validators.isValidPassword(s.password)) null else "Мінімум 8 символів"
        val nameErr = if (s.registerMode && s.name.isBlank()) "Введіть ім'я" else null
        if (emailErr != null || passErr != null || nameErr != null) {
            _s.update { it.copy(emailError = emailErr, passwordError = passErr, nameError = nameErr) }
            return
        }
        _s.update { it.copy(loading = true, serverError = null) }
        viewModelScope.launch {
            try {
                if (s.registerMode) repo.register(s.email, s.password, s.name) else repo.login(s.email, s.password)
                _s.update { it.copy(loading = false) }
                onSuccess()
            } catch (e: Exception) {
                _s.update { it.copy(loading = false, serverError = e.userMessage()) }
            }
        }
    }
}

@Composable
fun AuthScreen(onSuccess: () -> Unit, vm: AuthViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Подкасти", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            if (s.registerMode) "Створіть акаунт" else "Увійдіть до акаунту",
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 16.dp),
        )
        if (s.registerMode) {
            OutlinedTextField(
                s.name, vm::onName, label = { Text("Ім'я") }, singleLine = true,
                isError = s.nameError != null, supportingText = { s.nameError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            s.email, vm::onEmail, label = { Text("Пошта") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            isError = s.emailError != null, supportingText = { s.emailError?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            s.password, vm::onPassword, label = { Text("Пароль") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = s.passwordError != null, supportingText = { s.passwordError?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        s.serverError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp)) }
        Button(
            onClick = { vm.submit(onSuccess) }, enabled = !s.loading,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            if (s.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(if (s.registerMode) "Створити акаунт" else "Увійти")
        }
        TextButton(onClick = vm::toggleMode) {
            Text(if (s.registerMode) "Вже є акаунт? Увійти" else "Зареєструватися")
        }
    }
}
