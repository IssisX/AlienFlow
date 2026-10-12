# Approved Gray alien asset

The owner approved the generated image in this conversation. It is used without a redesign or a replacement face.

Original: menacing_metallic_alien_head_icon.png, 1254 × 1254 RGBA. SHA-256: 8ea348a1821cfeb4f33f39fed849a593cb14808c7623ce2dcfafaed4daed2305.
Packaged derivative: generated drawable/alien_approved.webp, 192 × 192, Lanczos downsample and WebP quality 86. SHA-256: 8850ac9bbd07c876ba00e5b4339d1b126a14d631194b07379dde0ac676888940.
The only transformations are size reduction and asset encoding. Both the floating bubble and launcher reference this drawable. The original remains attached to the conversation.

The derivative is stored as eight ordered base64 source fragments under artwork/approved-gray for verified connector transport. The per-variant Gradle PrepareApprovedAlien task joins and decodes them, verifies the complete image SHA-256, and writes the generated Android drawable before resource processing. The fragments are one encoded asset, not runtime state. No image is downloaded at runtime; no substitute image is built.
