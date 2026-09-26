package com.lovablepet.domain.auth.service;

import com.lovablepet.domain.auth.client.KakaoOAuthClient;
import com.lovablepet.domain.auth.client.KakaoTokenResponse;
import com.lovablepet.domain.auth.client.KakaoUserResponse;
import com.lovablepet.domain.auth.dto.AuthTokenResponse;
import com.lovablepet.domain.auth.entity.OAuthAccount;
import com.lovablepet.domain.auth.entity.OAuthProvider;
import com.lovablepet.domain.auth.repository.OAuthAccountRepository;
import com.lovablepet.domain.member.entity.Member;
import com.lovablepet.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KakaoAuthService {

    private final KakaoOAuthClient kakaoOAuthClient;
    private final OAuthAccountRepository oauthAccountRepository;
    private final MemberRepository memberRepository;
    private final AuthService authService;

    @Transactional
    public AuthTokenResponse login(String authorizationCode) {
        KakaoTokenResponse token = kakaoOAuthClient.exchangeAuthorizationCode(authorizationCode);
        KakaoUserResponse kakaoUser = kakaoOAuthClient.getUser(token.accessToken());
        String providerSubject = kakaoUser.id().toString();

        return oauthAccountRepository
                .findByProviderAndProviderSubject(OAuthProvider.KAKAO, providerSubject)
                .map(account -> authService.issueTokensForActiveMember(account.getMemberId()))
                .orElseGet(() -> createKakaoMember(kakaoUser, providerSubject));
    }

    private AuthTokenResponse createKakaoMember(KakaoUserResponse kakaoUser, String providerSubject) {
        Member member = Member.create(resolveNickname(kakaoUser));
        memberRepository.save(member);

        OAuthAccount account = OAuthAccount.create(member.getId(), OAuthProvider.KAKAO, providerSubject);
        oauthAccountRepository.save(account);

        return authService.issueTokensForActiveMember(member.getId());
    }

    private String resolveNickname(KakaoUserResponse kakaoUser) {
        String nickname = kakaoUser.nickname();
        if (nickname == null || nickname.isBlank() || nickname.length() > 50) {
            return "kakao-" + kakaoUser.id();
        }
        return nickname.trim();
    }
}
