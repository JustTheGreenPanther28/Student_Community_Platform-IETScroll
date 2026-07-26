const BASE = 'https://student-community-platform-ietscroll.onrender.com';
		let registeredEmail = '';

		// ── password toggle ──
		const pwInput = document.getElementById('password');
		const pwToggle = document.getElementById('pwToggle');
		pwToggle.addEventListener('click', () => {
			const show = pwInput.type === 'password';
			pwInput.type = show ? 'text' : 'password';
			pwToggle.textContent = show ? 'hide' : 'show';
		});

		// ── helpers ──
		function setError(id, msg) {
			const el = document.getElementById('err-' + id);
			const input = document.getElementById(id);
			if (msg) {
				el.textContent = msg;
				el.classList.add('show');
				input.classList.add('error');
			} else {
				el.classList.remove('show');
				input.classList.remove('error');
			}
		}

		function clearErrors() {
			['fullName', 'username', 'email', 'password', 'course', 'branch', 'yearOfPassout']
				.forEach(id => setError(id, ''));
		}

		function showAlert(type, msg) {
			const el = document.getElementById('formAlert');
			el.className = 'alert show ' + type;
			el.textContent = msg;
		}

		function hideAlert() {
			document.getElementById('formAlert').className = 'alert';
		}

		// ── validate ──
		function validate() {
			let ok = true;
			clearErrors();

			const fullName = document.getElementById('fullName').value.trim();
			const username = document.getElementById('username').value.trim();
			const email = document.getElementById('email').value.trim();
			const password = document.getElementById('password').value;
			const course = document.getElementById('course').value;
			const branch = document.getElementById('branch').value;
			const yearOfPassout = document.getElementById('yearOfPassout').value.trim();

			if (!fullName) {setError('fullName', 'Full name is required'); ok = false;}
			if (!username) {setError('username', 'Username is required'); ok = false;}

			if (!email || !email.endsWith('@ietdavv.edu.in')) {
				setError('email', 'Must be a valid @ietdavv.edu.in email'); ok = false;
			}

			if (!password || password.length < 8) {
				setError('password', 'Password must be at least 8 characters'); ok = false;
			}

			if (!course) {setError('course', 'Select your course'); ok = false;}
			if (!branch) {setError('branch', 'Select your branch'); ok = false;}

			const yr = parseInt(yearOfPassout);
			const currentYear = new Date().getFullYear();
			const minYear = currentYear - 2;
			const maxYear = currentYear + 10;

			if (!yearOfPassout || isNaN(yr) || yr < minYear || yr > maxYear) {
			  setError('yearOfPassout', `Enter a valid passout year (${minYear}–${maxYear})`);
			  ok = false;
			}

			return ok;
		}

		// ── register submit ──
		document.getElementById('registerForm').addEventListener('submit', async (e) => {
			e.preventDefault();
			hideAlert();
			if (!validate()) return;

			const btn = document.getElementById('submitBtn');
			btn.classList.add('loading');
			btn.disabled = true;

			const payload = {
				fullName: document.getElementById('fullName').value.trim(),
				username: document.getElementById('username').value.trim(),
				email: document.getElementById('email').value.trim(),
				password: document.getElementById('password').value,
				course: document.getElementById('course').value,
				branch: document.getElementById('branch').value,
				yearOfPassout: parseInt(document.getElementById('yearOfPassout').value.trim()),
			};

			try {
				const res = await fetch(`${BASE}/api/v1/user/register`, {
					method: 'POST',
					headers: {'Content-Type': 'application/json'},
					body: JSON.stringify(payload),
				});

				const data = await res.json();

				if (res.ok) {
					registeredEmail = payload.email;
					document.getElementById('otpEmailLabel').textContent = registeredEmail;
					document.getElementById('otpOverlay').classList.add('show');
				} else {
					const msg = data?.message || data?.error || 'Registration failed. Please try again.';
					showAlert('fail', msg);
				}
			} catch (err) {
				showAlert('fail', 'Could not reach the server. Please try again.');
			} finally {
				btn.classList.remove('loading');
				btn.disabled = false;
			}
		});

		document.getElementById('otpVerifyBtn').addEventListener('click', async () => {
			const otp = parseInt(document.getElementById('otpInput').value.trim());
			const alertEl = document.getElementById('otpAlert');
			const btn = document.getElementById('otpVerifyBtn');

			if (!otp || String(otp).length < 4) {
				alertEl.className = 'otp-alert fail';
				alertEl.textContent = 'Enter the 6-digit code from your email.';
				return;
			}

			btn.disabled = true;
			btn.textContent = 'Verifying…';
			alertEl.className = 'otp-alert';
			alertEl.textContent = '';

			try {
				const res = await fetch(`${BASE}/api/v1/otp/verify`, {
					method: 'POST',
					headers: {'Content-Type': 'application/json'},
					body: JSON.stringify({otp, email: registeredEmail}),
				});

				const data = await res.json();

				if (res.ok && data !== 'FAILED') {
					alertEl.className = 'otp-alert success';
					alertEl.textContent = '✓ Account verified! Redirecting to login…';
					setTimeout(() => {window.location.href = 'login.html';}, 1800);
				} else {
					alertEl.className = 'otp-alert fail';
					alertEl.textContent = 'Incorrect or expired OTP. Please try again.';
					btn.disabled = false;
					btn.textContent = 'Verify & Activate';
				}
			} catch {
				alertEl.className = 'otp-alert fail';
				alertEl.textContent = 'Server error. Please try again.';
				btn.disabled = false;
				btn.textContent = 'Verify & Activate';
			}
		});

		document.getElementById('otpInput').addEventListener('input', function () {
			this.value = this.value.replace(/\D/g, '').slice(0, 6);
		});
