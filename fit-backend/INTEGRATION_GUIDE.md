# Server Registration Integration Guide

## Architecture

The new API key management system allows server owners to:
1. Register their server through your webpage
2. Receive a unique API key
3. Use that key in their server configuration

## Backend Endpoints

### 1. Register a New Server (Public)
```
POST /v1/servers/register
Content-Type: application/json

{
  "server_name": "My Awesome Server",
  "owner_name": "Steve",
  "owner_email": "steve@example.com",
  "server_address": "play.example.com:25565",
  "server_version": "1.20.1"
}
```

**Response (201):**
```json
{
  "api_key": "abc123def456ghi789jkl...",
  "server_name": "My Awesome Server",
  "message": "Store this key securely in your server config. You won't be able to see it again!"
}
```

**Error Cases:**
- `409 Conflict` - Server name already registered
- `400 Bad Request` - Invalid data

⚠️ **IMPORTANT:** The API key is only shown ONCE. Server owners must save it immediately. You cannot retrieve it later.

---

### 2. Get Server Info (Authenticated)
```
GET /v1/servers/info
Headers: X-API-Key: <their_api_key>
```

**Response:**
```json
{
  "server_name": "My Awesome Server",
  "active": true,
  "created_at": "2026-01-14T10:30:00-06:00",
  "last_used": "2026-01-14T15:45:30-06:00"
}
```

---

## Frontend Implementation Example

### HTML Registration Form
```html
<form id="registerForm">
  <input type="text" id="serverName" placeholder="Server Name" required>
  <input type="text" id="ownerName" placeholder="Owner Name" required>
  <input type="email" id="ownerEmail" placeholder="Email" required>
  <input type="text" id="serverAddress" placeholder="Server Address (optional)">
  <input type="text" id="serverVersion" placeholder="Version (optional)">
  <button type="submit">Register Server</button>
</form>

<div id="result"></div>
```

### JavaScript Handler
```javascript
document.getElementById('registerForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  
  const payload = {
    server_name: document.getElementById('serverName').value,
    owner_name: document.getElementById('ownerName').value,
    owner_email: document.getElementById('ownerEmail').value,
    server_address: document.getElementById('serverAddress').value || null,
    server_version: document.getElementById('serverVersion').value || null,
  };
  
  try {
    const response = await fetch('https://your-api.com/v1/servers/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    
    if (!response.ok) {
      const error = await response.json();
      document.getElementById('result').innerHTML = 
        `<p style="color:red;">Error: ${error.detail}</p>`;
      return;
    }
    
    const data = await response.json();
    document.getElementById('result').innerHTML = `
      <div style="border: 2px solid green; padding: 15px;">
        <h3>✓ Server Registered!</h3>
        <p><strong>Server Name:</strong> ${data.server_name}</p>
        <p><strong>Your API Key:</strong></p>
        <code style="background: #f0f0f0; padding: 10px; display: block; 
                     word-break: break-all; font-family: monospace;">
          ${data.api_key}
        </code>
        <p style="color: red; font-weight: bold;">
          ⚠️ ${data.message}
        </p>
        <button onclick="copyToClipboard('${data.api_key}')">Copy Key</button>
      </div>
    `;
  } catch (error) {
    document.getElementById('result').innerHTML = 
      `<p style="color:red;">Network error: ${error.message}</p>`;
  }
});

function copyToClipboard(text) {
  navigator.clipboard.writeText(text);
  alert('API key copied to clipboard!');
}
```

---

## Key Security Considerations

### 1. One-Time Display
- The API key is **only shown once** after registration
- Store it in a `.env` file or secure config on the server
- Cannot be retrieved later (design feature, not a limitation)

### 2. Key Rotation
If a key is compromised:
```bash
# Via management script
python manage_keys.py disable <old_key>

# Create new key
python manage_keys.py add "My Awesome Server"
```

Then the server owner can update their config with the new key.

### 3. Email Confirmation (Optional)
Add this to your registration flow:
- Send email to `owner_email` with the API key
- Requires SMTP setup (not included yet)

Example addition to `main.py`:
```python
# After successful registration
send_registration_email(
    to_email=request.owner_email,
    server_name=request.server_name,
    api_key=api_key
)
```

---

## Payment Integration

To integrate with your payment system:

1. **Add payment status to `api_keys` table:**
   ```sql
   ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS payment_status TEXT DEFAULT 'pending';
   ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS payment_id TEXT;
   ```

2. **Create a new endpoint to finalize registration after payment:**
   ```python
   @app.post("/v1/servers/activate")
   def activate_server(payment_id: str, api_key: str):
       """Activate a server after payment is confirmed."""
       # Verify payment_id with your payment processor
       # Update payment_status to 'confirmed'
   ```

3. **Workflow:**
   - User registers → receives API key (status: "pending")
   - User completes payment
   - Your payment processor calls webhook `/v1/servers/activate`
   - Status changes to "confirmed", key becomes active

---

## Testing with curl

```bash
# Register a server
curl -X POST http://localhost:8000/v1/servers/register \
  -H "Content-Type: application/json" \
  -d '{
    "server_name": "Test Server",
    "owner_name": "TestUser",
    "owner_email": "test@example.com"
  }'

# Get server info (replace KEY with actual key)
curl -X GET "http://localhost:8000/v1/servers/info" \
  -H "X-API-Key: KEY"
```

---

## What's Next

1. ✅ API key generation and validation (implemented)
2. ⏳ Email confirmation to server owner
3. ⏳ Payment processor integration (Stripe, PayPal, etc.)
4. ⏳ Dashboard for server owners to manage their keys
5. ⏳ Key rotation/regeneration endpoint
