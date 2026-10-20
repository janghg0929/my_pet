// MY_PET 윈도우 실행기 (MyPet.exe 하나만 받으면 되게).
//
// - 처음 실행: GitHub 최신 릴리스의 MyPet-windows-x64.zip 을 받아 %LOCALAPPDATA%\MyPet 에 설치하고 실행한다.
// - 그다음부터: 설치된 %LOCALAPPDATA%\MyPet\MyPet.exe 를 바로 실행한다.
// 앱 업데이트(MyPet.jar)는 설치된 앱이 스스로 하므로 여기서는 하지 않는다.
//
// 개발/시험용: MyPet.exe --install-only (설치만 하고 실행하지 않음),
//              환경 변수 MYPET_ZIP_URL (릴리스 대신 다른 주소에서 받기)
//
// 자바 없이 돌아야 해서 윈도우에 기본으로 들어 있는 .NET Framework 4 로 만든다 (C# 5 문법만 사용).
// 빌드: scripts\build-launcher.ps1

using System;
using System.ComponentModel;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Threading;
using System.Windows.Forms;

[assembly: System.Reflection.AssemblyTitle("MY_PET")]
[assembly: System.Reflection.AssemblyProduct("MY_PET")]

static class MyPetLauncher
{
    const string ZipUrl = "https://github.com/janghg0929/my_pet/releases/latest/download/MyPet-windows-x64.zip";
    const string ReleasesUrl = "https://github.com/janghg0929/my_pet/releases/latest";

    /** 설치 폴더: Java 앱(Constants.appDataDir)과 같은 규칙 - %LOCALAPPDATA%\MyPet */
    static string HomeDirectory()
    {
        string local = Environment.GetEnvironmentVariable("LOCALAPPDATA");
        if (string.IsNullOrEmpty(local))
        {
            local = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        }
        return Path.Combine(local, "MyPet");
    }

    static string ZipDownloadUrl()
    {
        string custom = Environment.GetEnvironmentVariable("MYPET_ZIP_URL");
        return string.IsNullOrEmpty(custom) ? ZipUrl : custom;
    }

    [STAThread]
    static int Main(string[] args)
    {
        string home = HomeDirectory();
        string app = Path.Combine(home, "MyPet.exe");
        bool installOnly = Array.IndexOf(args, "--install-only") >= 0;

        if (!IsInstalled(home))
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            InstallForm form = new InstallForm(home);
            Application.Run(form);
            if (!form.Succeeded)
            {
                return 1;
            }
        }
        if (installOnly)
        {
            return 0;
        }

        try
        {
            ProcessStartInfo start = new ProcessStartInfo(app, JoinArgs(args));
            start.WorkingDirectory = home;
            start.UseShellExecute = false;
            Process.Start(start);
            return 0;
        }
        catch (Exception e)
        {
            MessageBox.Show("비트를 실행하지 못했어요.\n\n" + e.Message, "MY_PET", MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }
    }

    /** 설치된 앱(실행 파일 + 자바 런타임 + 앱 파일)이 모두 있는지. */
    static bool IsInstalled(string home)
    {
        return File.Exists(Path.Combine(home, "MyPet.exe"))
            && File.Exists(Path.Combine(home, "app", "MyPet.cfg"))
            && Directory.Exists(Path.Combine(home, "runtime"));
    }

    static string JoinArgs(string[] args)
    {
        string[] quoted = new string[args.Length];
        for (int i = 0; i < args.Length; i++)
        {
            quoted[i] = "\"" + args[i].Replace("\"", "\\\"") + "\"";
        }
        return string.Join(" ", quoted);
    }

    /** "비트를 설치하는 중" 진행 창. 내려받기 → 압축 풀기 → 닫힘. */
    sealed class InstallForm : Form
    {
        readonly string home;
        readonly Label message;
        readonly ProgressBar bar;
        public bool Succeeded;

        public InstallForm(string home)
        {
            this.home = home;
            Text = "MY_PET 설치";
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            MinimizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            ClientSize = new Size(380, 110);
            BackColor = Color.FromArgb(0xFB, 0xF6, 0xEC);
            Font = new Font("Malgun Gothic", 9.5f);
            TopMost = true;

            message = new Label();
            message.Text = "비트를 처음 설치하는 중이에요... (한 번만 해요)";
            message.SetBounds(18, 18, 344, 22);
            Controls.Add(message);

            bar = new ProgressBar();
            bar.SetBounds(18, 50, 344, 20);
            bar.Minimum = 0;
            bar.Maximum = 100;
            Controls.Add(bar);

            Label where = new Label();
            where.Text = "설치 위치: " + home;
            where.ForeColor = Color.FromArgb(0x7D, 0x74, 0x68);
            where.Font = new Font("Malgun Gothic", 8f);
            where.SetBounds(18, 80, 344, 20);
            Controls.Add(where);
        }

        protected override void OnShown(EventArgs e)
        {
            base.OnShown(e);
            Thread worker = new Thread(Run);
            worker.IsBackground = true;
            worker.Start();
        }

        void SetStatus(string text, int percent)
        {
            if (IsDisposed)
            {
                return;
            }
            BeginInvoke((MethodInvoker)delegate
            {
                message.Text = text;
                if (percent < 0)
                {
                    bar.Style = ProgressBarStyle.Marquee;
                }
                else
                {
                    bar.Style = ProgressBarStyle.Continuous;
                    bar.Value = Math.Max(0, Math.Min(100, percent));
                }
            });
        }

        void Run()
        {
            string zip = Path.Combine(Path.GetTempPath(), "MyPet-windows-x64-" + Guid.NewGuid().ToString("N") + ".zip");
            try
            {
                // GitHub 은 TLS 1.2 이상만 받는다 (.NET 4 기본값이 더 낮을 수 있음)
                ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072;
                Download(zip);
                SetStatus("압축을 푸는 중이에요...", -1);
                Extract(zip, home);
                if (!IsInstalled(home))
                {
                    throw new InvalidDataException("내려받은 파일에 비트 프로그램이 없어요.");
                }
                Succeeded = true;
                BeginInvoke((MethodInvoker)Close);
            }
            catch (Exception e)
            {
                BeginInvoke((MethodInvoker)delegate
                {
                    DialogResult answer = MessageBox.Show(this,
                        "비트를 설치하지 못했어요.\n인터넷 연결을 확인하고 다시 실행해 주세요.\n\n" + e.Message
                            + "\n\n다운로드 페이지를 열까요?",
                        "MY_PET 설치", MessageBoxButtons.YesNo, MessageBoxIcon.Error);
                    if (answer == DialogResult.Yes)
                    {
                        try { Process.Start(ReleasesUrl); } catch (Exception) { }
                    }
                    Close();
                });
            }
            finally
            {
                try { File.Delete(zip); } catch (Exception) { }
            }
        }

        void Download(string target)
        {
            using (WebClient client = new WebClient())
            {
                client.Headers.Add("User-Agent", "MY_PET-launcher");
                ManualResetEvent done = new ManualResetEvent(false);
                Exception failure = null;
                client.DownloadProgressChanged += delegate (object sender, DownloadProgressChangedEventArgs e)
                {
                    long mb = e.BytesReceived / (1024 * 1024);
                    long totalMb = e.TotalBytesToReceive > 0 ? e.TotalBytesToReceive / (1024 * 1024) : 0;
                    string size = totalMb > 0 ? mb + " / " + totalMb + "MB" : mb + "MB";
                    SetStatus("비트를 처음 설치하는 중이에요... " + size, e.TotalBytesToReceive > 0 ? e.ProgressPercentage : -1);
                };
                client.DownloadFileCompleted += delegate (object sender, AsyncCompletedEventArgs e)
                {
                    failure = e.Error;
                    done.Set();
                };
                client.DownloadFileAsync(new Uri(ZipDownloadUrl()), target);
                done.WaitOne();
                if (failure != null)
                {
                    throw failure;
                }
            }
        }

        /** zip 안의 "MyPet/..." 를 home 아래로 푼다 (맨 앞 폴더 이름은 뺀다). */
        static void Extract(string zipPath, string home)
        {
            Directory.CreateDirectory(home);
            string root = Path.GetFullPath(home) + Path.DirectorySeparatorChar;
            using (ZipArchive archive = ZipFile.OpenRead(zipPath))
            {
                foreach (ZipArchiveEntry entry in archive.Entries)
                {
                    string name = entry.FullName.Replace('\\', '/');
                    int slash = name.IndexOf('/');
                    if (slash < 0)
                    {
                        continue;
                    }
                    string relative = name.Substring(slash + 1);
                    if (relative.Length == 0)
                    {
                        continue;
                    }
                    string target = Path.GetFullPath(Path.Combine(home, relative.Replace('/', Path.DirectorySeparatorChar)));
                    if (!target.StartsWith(root, StringComparison.OrdinalIgnoreCase))
                    {
                        continue; // 설치 폴더 밖으로 나가는 경로는 무시
                    }
                    if (name.EndsWith("/"))
                    {
                        Directory.CreateDirectory(target);
                        continue;
                    }
                    Directory.CreateDirectory(Path.GetDirectoryName(target));
                    entry.ExtractToFile(target, true);
                }
            }
        }
    }
}
