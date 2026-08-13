<template>
  <div class="auth-layout">
    <div class="auth-box">
      <h1 class="auth-title">智慧冰箱与智能菜谱系统</h1>
      <router-view />
    </div>
  </div>
</template>

<style scoped>
.auth-layout {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f6feb 0%, #35c0c0 100%);
}
.auth-box {
  width: 420px;
  padding: 32px 28px 20px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.15);
}
.auth-title {
  text-align: center;
  font-size: 20px;
  margin: 0 0 20px;
  color: #303133;
}
</style>
