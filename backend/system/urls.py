from django.urls import path
from rest_framework_simplejwt.views import TokenObtainPairView, TokenRefreshView
from . import views

app_name = 'system'

urlpatterns = [
    # Authentication endpoints
    path('token/', views.LoginView.as_view(), name='login'),
    path('token/refresh/', TokenRefreshView.as_view(), name='token_refresh'),
    
    # User management endpoints (admin only)
    path('user/', views.UserListCreateView.as_view(), name='user_list_create'),
    path('user/<int:pk>/', views.UserDetailView.as_view(), name='user_detail'),
    
    # Self endpoints
    path('self/', views.SelfView.as_view(), name='self'),
    path('self/setpass/', views.PasswordChangeView.as_view(), name='password_change'),
]
