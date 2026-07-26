const BASE = 'https://student-community-platform-ietscroll.onrender.com';
		//const BASE = "http://localhost:4040";
		
		//PASSWORD => HIDE/SHOW
		const pwInput = document.getElementById('password');
		const pwToggle = document.getElementById('pwToggle');
		pwToggle.addEventListener('click', () => {
			const show = pwInput.type === 'password';
			pwInput.type = show ? 'text' : 'password';
			pwToggle.textContent = show ? 'hide' : 'show';
		});

		function setError(id, msg) {
			const err = document.getElementById('err-' + id);
			const input = document.getElementById(id);
			if (msg) {
				err.textContent = msg;
				err.classList.add('show');
				input.classList.add('error');
			} else {
				err.classList.remove('show');
				input.classList.remove('error');
			}
		}

		function showAlert(type, msg) {
			const el = document.getElementById('formAlert');
			el.className = 'alert show ' + type;
			el.textContent = msg;
		}

		function validate() {
			let ok = true;
			const email = document.getElementById('email').value.trim();
			const password = document.getElementById('password').value;

			setError('email', '');
			setError('password', '');

			if (!email) {
				setError('email', 'Must be a valid @ietdavv.edu.in email');
				ok = false;
			}
			if (!password) {
				setError('password', 'Password is required');
				ok = false;
			}
			return ok;
		}

		document.getElementById('loginForm').addEventListener('submit', async (e) => {
			e.preventDefault();
			if (!validate()) return;

			const btn = document.getElementById('submitBtn');
			btn.classList.add('loading');
			btn.disabled = true;
			document.getElementById('formAlert').className = 'alert';

			const payload = {
				email: document.getElementById('email').value.trim(),
				password: document.getElementById('password').value,
			};

			try {
				const res = await fetch(`${BASE}/login`, {
					method: 'POST',
					headers: {'Content-Type': 'application/json'},
					body: JSON.stringify(payload),
				});

				if (res.ok) {

					let token = res.headers.get("Authorization");
					console.log("HEADER TOKEN:", token);

					if (token) {
						token = token.replace("Bearer ", "").trim();
						localStorage.setItem("iet_token", token);
					} else {
						console.error("No token in header!");
					}

					showAlert('success', 'Login successful! Redirecting…');

					setTimeout(() => {
						window.location.href = BASE + '/dashboard.html';
					}, 1400);
				}
			} catch {
				showAlert('fail', 'Could not reach the server. Please try again.');
			} finally {
				btn.classList.remove('loading');
				btn.disabled = false;
			}
		});
